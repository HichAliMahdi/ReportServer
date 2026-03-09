package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JRXML Validator - Prevents malicious JRXML uploads that attempt code injection
 * 
 * JRXML files can execute arbitrary Java code via<expression> tags and new() expressions.
 * This validator restricts which classes can be instantiated to prevent exploitation.
 */
@Service
public class JrxmlValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(JrxmlValidator.class);
    
    // Dangerous patterns that could allow code execution
    private static final String[] DANGEROUS_PATTERNS = {
        "Runtime.getRuntime()",
        "File(",
        "FileInputStream(",
        "FileOutputStream(",
        "ProcessBuilder(",
        "ProcessImpl(",
        "System.load",
        "System.exec",
        "Class.forName",
        "Reflection",
        "Method.invoke",
        "Constructor.newInstance",
        "URLClassLoader",
        "URLConnection",
        "Socket(",
        "Runtime",
        "ProcessBuilder",
        "ScriptEngineManager"
    };
    
    @Value("${reportserver.jrxml.allowed-classes:java.lang.String,java.lang.Integer,java.lang.Double,java.lang.Boolean,java.lang.Math,java.lang.System}")
    private String allowedClassesConfig;
    
    private Set<String> allowedClasses;
    
    /**
     * Initialize allowed classes from configuration
     */
    private synchronized Set<String> getAllowedClasses() {
        if (allowedClasses == null) {
            allowedClasses = new HashSet<>(
                Arrays.asList(allowedClassesConfig.split(","))
            );
            // Trim whitespace
            allowedClasses = allowedClasses.stream()
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        }
        return allowedClasses;
    }
    
    /**
     * Validate JRXML content for security issues
     * @param jrxmlContent the JRXML file content
     * @return JrxmlValidationResult with validation status and any issues found
     */
    public JrxmlValidationResult validate(String jrxmlContent) {
        JrxmlValidationResult result = new JrxmlValidationResult();
        
        try {
            // First check for obvious dangerous patterns
            for (String pattern : DANGEROUS_PATTERNS) {
                if (jrxmlContent.contains(pattern)) {
                    result.addIssue("DANGER: Found dangerous pattern: " + pattern);
                }
            }
            
            // Parse XML to check for dangerous expressions
            validateXmlContent(jrxmlContent, result);
            
            if (result.hasIssues()) {
                logger.warn("JRXML validation failed with {} issues", result.getIssues().size());
                result.valid = false;
            } else {
                result.valid = true;
                logger.info("JRXML validation passed");
            }
            
        } catch (Exception e) {
            logger.error("JRXML validation error", e);
            result.addIssue("Validation error: " + e.getMessage());
            result.valid = false;
        }
        
        return result;
    }
    
    /**
     * Validate XML structure and expressions in JRXML
     */
    private void validateXmlContent(String jrxmlContent, JrxmlValidationResult result) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable DTD processing to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                jrxmlContent.getBytes(StandardCharsets.UTF_8)
            ));
            
            // Check for expressions that might contain dangerous code
            NodeList expressionNodes = doc.getElementsByTagName("expression");
            for (int i = 0; i < expressionNodes.getLength(); i++) {
                String expression = expressionNodes.item(i).getTextContent();
                validateExpression(expression, result);
            }
            
            // Check parameters
            NodeList parameterNodes = doc.getElementsByTagName("parameter");
            for (int i = 0; i < parameterNodes.getLength(); i++) {
                Element param = (Element) parameterNodes.item(i);
                String classAttr = param.getAttribute("class");
                validateClassReference(classAttr, result);
            }
            
            // Check for function definitions
            NodeList functionNodes = doc.getElementsByTagName("function");
            for (int i = 0; i < functionNodes.getLength(); i++) {
                Element func = (Element) functionNodes.item(i);
                String classAttr = func.getAttribute("class");
                validateClassReference(classAttr, result);
            }
            
        } catch (Exception e) {
            logger.debug("XML validation failed: {}", e.getMessage());
            // XML parsing failed - this might be invalid JRXML
            // Continue checking with string patterns
        }
    }
    
    /**
     * Validate an expression for dangerous code
     */
    private void validateExpression(String expression, JrxmlValidationResult result) {
        if (expression == null || expression.trim().isEmpty()) {
            return;
        }
        
        // Check for new object instantiation
        if (expression.contains(" new ")) {
            // new() is potentially dangerous - check which class
            String[] parts = expression.split(" new ");
            for (int i = 1; i < parts.length; i++) {
                String className = parts[i].trim().split("[\\s\\(\\.]")[0];
                
                if (!getAllowedClasses().contains(className) && !isJavaLangClass(className)) {
                    result.addIssue("WARNING: Attempted instantiation of class: " + className);
                }
            }
        }
        
        // Check for dangerous method calls
        for (String pattern : DANGEROUS_PATTERNS) {
            if (expression.contains(pattern)) {
                result.addIssue("WARNING: Found dangerous pattern in expression: " + pattern);
            }
        }
    }
    
    /**
     * Validate a class reference (parameter or function class attribute)
     */
    private void validateClassReference(String className, JrxmlValidationResult result) {
        if (className == null || className.trim().isEmpty()) {
            return;
        }
        
        // Allow common Java types
        String[] safeClasses = {
            "java.lang.String", "java.lang.Integer", "java.lang.Double", 
            "java.lang.Boolean", "java.lang.Long", "java.lang.Float",
            "java.sql.Timestamp", "java.util.Date", "java.math.BigDecimal",
            "java.lang.Object"
        };
        
        boolean isSafe = false;
        for (String safe : safeClasses) {
            if (className.equals(safe)) {
                isSafe = true;
                break;
            }
        }
        
        if (!isSafe && !getAllowedClasses().contains(className)) {
            result.addIssue("WARNING: Non-whitelisted class reference: " + className);
        }
    }
    
    /**
     * Check if a class is a safe java.lang class
     */
    private boolean isJavaLangClass(String className) {
        try {
            Class<?> cls = Class.forName(className);
            // Only allow classes from java.lang, java.util, java.math, java.sql
            String pkg = cls.getPackage().getName();
            return pkg.startsWith("java.lang") || 
                   pkg.startsWith("java.math") || 
                   pkg.startsWith("java.util") ||
                   pkg.startsWith("java.sql");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Validation result object
     */
    public static class JrxmlValidationResult {
        public boolean valid;
        private Set<String> issues = new HashSet<>();
        
        public void addIssue(String issue) {
            issues.add(issue);
        }
        
        public Set<String> getIssues() {
            return issues;
        }
        
        public boolean hasIssues() {
            return !issues.isEmpty();
        }
        
        @Override
        public String toString() {
            return "JrxmlValidationResult{" +
                    "valid=" + valid +
                    ", issues=" + issues +
                    '}';
        }
    }
}
