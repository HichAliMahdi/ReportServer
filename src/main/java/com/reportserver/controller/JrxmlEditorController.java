package com.reportserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/jrxml")
public class JrxmlEditorController {
    
    private static final Logger logger = LoggerFactory.getLogger(JrxmlEditorController.class);
    private static final String UPLOAD_DIR = "data/reports/";

    /**
     * Load JRXML file content for editing
     */
    @GetMapping("/load/{fileName}")
    @ResponseBody
    public ResponseEntity<?> loadJrxmlContent(@PathVariable String fileName) {
        try {
            // Validate file name
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid file name"));
            }

            File file = new File(UPLOAD_DIR + fileName);
            
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found: {}", fileName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "File not found"));
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "content", content,
                "fileName", fileName
            ));
            
        } catch (Exception e) {
            logger.error("Error loading JRXML file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Save edited JRXML file content
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> saveJrxmlContent(
            @RequestParam String fileName,
            @RequestParam String content) {
        try {
            // Validate file name
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid file name"));
            }

            File file = new File(UPLOAD_DIR + fileName);
            
            // Write content to file
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }
            
            logger.info("Successfully saved JRXML file: {}", fileName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "File saved successfully"
            ));
            
        } catch (Exception e) {
            logger.error("Error saving JRXML file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Extract parameters from JRXML file
     */
    @GetMapping("/parameters/{fileName}")
    @ResponseBody
    public ResponseEntity<?> extractParameters(@PathVariable String fileName) {
        try {
            // Validate file name
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid file name"));
            }

            File file = new File(UPLOAD_DIR + fileName);
            
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found: {}", fileName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "File not found"));
            }

            List<Map<String, String>> parameters = parseJrxmlParameters(file);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "parameters", parameters
            ));
            
        } catch (Exception e) {
            logger.error("Error extracting parameters from JRXML file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Parse JRXML file and extract parameter definitions
     */
    private List<Map<String, String>> parseJrxmlParameters(File jrxmlFile) throws Exception {
        List<Map<String, String>> parameters = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(jrxmlFile);

        NodeList parameterNodes = document.getElementsByTagName("parameter");

        for (int i = 0; i < parameterNodes.getLength(); i++) {
            Element paramElement = (Element) parameterNodes.item(i);
            String name = paramElement.getAttribute("name");
            String className = paramElement.getAttribute("class");

            // Skip built-in report engine parameters
            if (name.startsWith("REPORT_") || name.equals("JASPER_REPORT")) {
                continue;
            }

            Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("class", className);
            param.put("inputType", determineInputType(className));

            // Extract default value if present
            NodeList defaultValueNodes = paramElement.getElementsByTagName("defaultValueExpression");
            if (defaultValueNodes.getLength() > 0) {
                String defaultValue = defaultValueNodes.item(0).getTextContent();
                param.put("defaultValue", defaultValue);
            }

            parameters.add(param);
        }

        return parameters;
    }

    /**
     * Determine HTML input type based on Java class
     */
    private String determineInputType(String javaClass) {
        if (javaClass == null || javaClass.isEmpty()) {
            return "text";
        }

        if (javaClass.contains("Date") || javaClass.contains("Timestamp")) {
            return "date";
        } else if (javaClass.contains("Time")) {
            return "time";
        } else if (javaClass.contains("Boolean")) {
            return "checkbox";
        } else if (javaClass.contains("Integer") || javaClass.contains("Long") || 
                   javaClass.contains("Double") || javaClass.contains("Float") ||
                   javaClass.contains("BigDecimal") || javaClass.contains("BigInteger")) {
            return "number";
        } else {
            return "text";
        }
    }
}
