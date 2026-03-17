package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * JRXML Validator - Prevents malicious JRXML uploads that attempt code injection
 * 
 * JRXML files can execute arbitrary Java code via<expression> tags and new() expressions.
 * This validator restricts which classes can be instantiated to prevent exploitation.
 */
@Service
public class JrxmlValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(JrxmlValidator.class);
    private static final int MAX_EXPRESSION_LENGTH = 1500;
    
    // High-risk patterns that can lead to arbitrary code execution or SSRF/file access.
    private static final String[] DANGEROUS_PATTERNS = {
        "Runtime.getRuntime()",
        "System.getProperty(",
        "System.getenv(",
        "File(",
        "FileInputStream(",
        "FileOutputStream(",
        "ProcessBuilder(",
        "ProcessBuilder ",
        "System.load",
        "System.exec",
        "Class.forName",
        "getClassLoader(",
        "ClassLoader",
        "java.io",
        "java.nio",
        "java.net",
        "java.lang.reflect",
        "java.security",
        "groovy.lang",
        "javax.script",
        "org.springframework",
        "org.apache.commons.io",
        "Method.invoke",
        "Constructor.newInstance",
        "URLClassLoader",
        "URLConnection",
        "Socket(",
        "http://",
        "https://",
        "ScriptEngineManager"
    };

    private static final Set<String> FORBIDDEN_TAGS = Set.of(
        "scriptlet",
        "import",
        "propertyExpression"
    );

    private static final Set<String> FORBIDDEN_ATTRIBUTES = Set.of(
        "scriptletClass",
        "formatFactoryClass"
    );

    private static final Pattern SAFE_EXPRESSION_CHARS = Pattern.compile("^[\\w\\s\\$\\{\\}\\[\\]\\(\\)\\.,:+\\-*/%<>=!&|\"'?#@]+$");
    
    @Value("${reportserver.jrxml.allowed-classes:java.lang.String,java.lang.Integer,java.lang.Double,java.lang.Boolean,java.lang.Math,java.lang.System}")
    private String allowedClassesConfig;

    @Value("${reportserver.jrxml.max-size-bytes:1048576}")
    private int maxJrxmlSizeBytes;
    
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
            if (jrxmlContent == null || jrxmlContent.trim().isEmpty()) {
                result.addIssue("JRXML content is empty");
                result.valid = false;
                return result;
            }

            if (jrxmlContent.getBytes(StandardCharsets.UTF_8).length > maxJrxmlSizeBytes) {
                result.addIssue("JRXML exceeds maximum allowed size");
                result.valid = false;
                return result;
            }

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
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                jrxmlContent.getBytes(StandardCharsets.UTF_8)
            ));
            
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Node node = allElements.item(i);
                if (!(node instanceof Element element)) {
                    continue;
                }

                String nodeName = element.getNodeName();
                String normalizedNodeName = nodeName == null ? "" : nodeName.toLowerCase(Locale.ROOT);

                if (FORBIDDEN_TAGS.contains(normalizedNodeName)) {
                    result.addIssue("DANGER: Forbidden JRXML tag used: " + nodeName);
                }

                for (String attributeName : FORBIDDEN_ATTRIBUTES) {
                    if (element.hasAttribute(attributeName)) {
                        result.addIssue("DANGER: Forbidden JRXML attribute used: " + attributeName);
                    }
                }

                if (element.hasAttribute("class")) {
                    validateClassReference(element.getAttribute("class"), result);
                }

                if (normalizedNodeName.contains("expression")) {
                    validateExpression(element.getTextContent(), result);
                }

                if ("jasperreport".equals(normalizedNodeName) && element.hasAttribute("language")) {
                    String language = element.getAttribute("language");
                    if (language != null && !language.isBlank() && !"java".equalsIgnoreCase(language.trim())) {
                        result.addIssue("DANGER: Unsupported JRXML expression language: " + language);
                    }
                }
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

        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            result.addIssue("WARNING: Expression too long for sandbox policy");
        }

        if (!SAFE_EXPRESSION_CHARS.matcher(expression).matches()) {
            result.addIssue("WARNING: Expression contains disallowed characters");
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
        return className.startsWith("java.lang.") ||
            className.startsWith("java.math.") ||
            className.startsWith("java.util.") ||
            className.startsWith("java.sql.");
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
