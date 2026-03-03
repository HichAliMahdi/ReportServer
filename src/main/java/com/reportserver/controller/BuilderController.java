package com.reportserver.controller;

import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.JrxmlBuilderService;
import com.reportserver.service.SchemaIntrospectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/api/builder")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class BuilderController {
    
    private static final Logger logger = LoggerFactory.getLogger(BuilderController.class);
    private static final String UPLOAD_DIR = "data/reports/";
    private static final String IMAGES_DIR = "data/images/";
    private static final String TEMPLATES_DIR = "data/templates/";

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private SchemaIntrospectionService schemaIntrospectionService;

    @Autowired
    private JrxmlBuilderService jrxmlBuilderService;

    /**
     * Get all tables from a datasource
     */
    @GetMapping("/datasources/{datasourceId}/tables")
    @ResponseBody
    public ResponseEntity<?> getTables(@PathVariable Long datasourceId) {
        Connection connection = null;
        try {
            logger.info("Fetching tables for datasource ID: {}", datasourceId);
            
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<String> tables = schemaIntrospectionService.getTables(connection);
            
            return ResponseEntity.ok(Map.of("success", true, "tables", tables));
            
        } catch (Exception e) {
            logger.error("Error fetching tables", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Get all columns for a specific table
     */
    @GetMapping("/datasources/{datasourceId}/tables/{tableName}/columns")
    @ResponseBody
    public ResponseEntity<?> getColumns(
            @PathVariable Long datasourceId,
            @PathVariable String tableName) {
        
        Connection connection = null;
        try {
            logger.info("Fetching columns for table {} from datasource ID: {}", tableName, datasourceId);
            
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<Map<String, String>> columns = schemaIntrospectionService.getColumns(connection, tableName);
            
            return ResponseEntity.ok(Map.of("success", true, "columns", columns));
            
        } catch (Exception e) {
            logger.error("Error fetching columns", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Generate a JRXML file based on selected table, columns, parameters, and variables
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<?> generateReport(
            @RequestParam String reportName,
            @RequestParam String tableName,
            @RequestParam List<String> columns,
            @RequestParam Long datasourceId,
            @RequestParam(required = false) String parametersJson,
            @RequestParam(required = false) String variablesJson) {
        
        Connection connection = null;
        try {
            logger.info("Generating JRXML for table {} with {} columns", tableName, columns.size());
            
            // Validate inputs
            if (reportName == null || reportName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Report name is required"));
            }
            
            if (!reportName.endsWith(".jrxml")) {
                reportName += ".jrxml";
            }
            
            if (columns == null || columns.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "At least one column must be selected"));
            }

            // Get column details from database
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<Map<String, String>> allColumns = schemaIntrospectionService.getColumns(connection, tableName);
            
            // Filter to only selected columns
            List<Map<String, String>> selectedColumns = new ArrayList<>();
            for (String columnName : columns) {
                for (Map<String, String> col : allColumns) {
                    if (col.get("name").equals(columnName)) {
                        selectedColumns.add(col);
                        break;
                    }
                }
            }

            // Parse parameters from JSON if provided
            List<ParameterDTO> parameters = new ArrayList<>();
            if (parametersJson != null && !parametersJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    parameters = objectMapper.readValue(parametersJson, new TypeReference<List<ParameterDTO>>() {});
                    logger.info("Parsed {} parameter(s) from JSON", parameters.size());
                } catch (Exception e) {
                    logger.error("Error parsing parameters JSON", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid parameters format: " + e.getMessage()));
                }
            }

            // Parse variables from JSON if provided
            List<VariableDTO> variables = new ArrayList<>();
            if (variablesJson != null && !variablesJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    variables = objectMapper.readValue(variablesJson, new TypeReference<List<VariableDTO>>() {});
                    logger.info("Parsed {} variable(s) from JSON", variables.size());
                } catch (Exception e) {
                    logger.error("Error parsing variables JSON", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid variables format: " + e.getMessage()));
                }
            }

            // Generate JRXML content
            String jrxmlContent = jrxmlBuilderService.generateJrxml(
                reportName.replace(".jrxml", ""), 
                tableName, 
                selectedColumns,
                parameters,
                variables
            );

            // Save to file
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(UPLOAD_DIR + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            logger.info("Successfully generated JRXML file: {}", reportName);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Report generated successfully",
                "reportName", reportName
            ));
            
        } catch (Exception e) {
            logger.error("Error generating JRXML", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Download a generated JRXML file
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadJrxml(@PathVariable String fileName) {
        try {
            // Validate file name to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            File file = new File(UPLOAD_DIR + fileName);
            
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
                
        } catch (Exception e) {
            logger.error("Error downloading file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload an image for use in visual reports (logos, backgrounds, etc.)
     */
    @PostMapping("/upload-image")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file type (images only)
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                response.put("success", false);
                response.put("message", "Only image files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            // Create images directory if it doesn't exist
            File imagesDir = new File(IMAGES_DIR);
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            // Generate unique filename to avoid overwrites
            String fileExtension = "";
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                fileExtension = originalFilename.substring(dotIndex);
            }
            
            String fileName = System.currentTimeMillis() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = Paths.get(IMAGES_DIR + fileName);
            
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Successfully uploaded image: {}", fileName);
            
            response.put("success", true);
            response.put("message", "Image uploaded successfully");
            response.put("fileName", fileName);
            response.put("filePath", IMAGES_DIR + fileName);
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            logger.error("Error uploading image", e);
            response.put("success", false);
            response.put("message", "Error uploading image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get list of all uploaded images
     */
    @GetMapping("/images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listImages() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            File imagesDir = new File(IMAGES_DIR);
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            File[] files = imagesDir.listFiles((dir, name) -> 
                name.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp|svg)$"));
            
            List<Map<String, String>> imageList = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    Map<String, String> imageInfo = new HashMap<>();
                    imageInfo.put("name", file.getName());
                    imageInfo.put("path", IMAGES_DIR + file.getName());
                    imageInfo.put("size", String.valueOf(file.length()));
                    imageList.add(imageInfo);
                }
            }
            
            response.put("success", true);
            response.put("images", imageList);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error listing images", e);
            response.put("success", false);
            response.put("message", "Error listing images: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Delete an image
     */
    @DeleteMapping("/images/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteImage(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file name to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            File file = new File(IMAGES_DIR + fileName);
            if (!file.exists()) {
                response.put("success", false);
                response.put("message", "Image not found");
                return ResponseEntity.notFound().build();
            }

            if (file.delete()) {
                logger.info("Successfully deleted image: {}", fileName);
                response.put("success", true);
                response.put(" message", "Image deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete image");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting image: {}", fileName, e);
            response.put("success", false);
            response.put("message", "Error deleting image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Save a visual report template (design configuration as JSON)
     */
    @PostMapping("/templates/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTemplate(@RequestBody Map<String, Object> templateData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String templateName = (String) templateData.get("name");
            if (templateName == null || templateName.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Template name is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Create templates directory if it doesn't exist
            File templatesDir = new File(TEMPLATES_DIR);
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }

            // Save template as JSON
            String fileName = templateName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
            File templateFile = new File(TEMPLATES_DIR + fileName);
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(templateFile, templateData);
            
            logger.info("Successfully saved template: {}", fileName);
            
            response.put("success", true);
            response.put("message", "Template saved successfully");
            response.put("fileName", fileName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error saving template", e);
            response.put("success", false);
            response.put("message", "Error saving template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get list of all templates
     */
    @GetMapping("/templates")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listTemplates() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            File templatesDir = new File(TEMPLATES_DIR);
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }

            File[] files = templatesDir.listFiles((dir, name) -> name.endsWith(".json"));
            
            List<Map<String, Object>> templateList = new ArrayList<>();
            if (files != null) {
                ObjectMapper mapper = new ObjectMapper();
                for (File file : files) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> templateData = mapper.readValue(file, Map.class);
                        templateData.put("fileName", file.getName());
                        templateList.add(templateData);
                    } catch (Exception e) {
                        logger.error("Error reading template file: {}", file.getName(), e);
                    }
                }
            }
            
            response.put("success", true);
            response.put("templates", templateList);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error listing templates", e);
            response.put("success", false);
            response.put("message", "Error listing templates: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Load a specific template
     */
    @GetMapping("/templates/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> loadTemplate(@PathVariable String fileName) {
        try {
            // Validate file name to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid file name"));
            }

            File file = new File(TEMPLATES_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> templateData = mapper.readValue(file, Map.class);
            
            return ResponseEntity.ok(templateData);
            
        } catch (Exception e) {
            logger.error("Error loading template: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error loading template: " + e.getMessage()));
        }
    }

    /**
     * Delete a template
     */
    @DeleteMapping("/templates/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file name to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            File file = new File(TEMPLATES_DIR + fileName);
            if (!file.exists()) {
                response.put("success", false);
                response.put("message", "Template not found");
                return ResponseEntity.notFound().build();
            }

            if (file.delete()) {
                logger.info("Successfully deleted template: {}", fileName);
                response.put("success", true);
                response.put("message", "Template deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete template");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting template: {}", fileName, e);
            response.put("success", false);
            response.put("message", "Error deleting template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Generate JRXML from visual builder design
     */
    @PostMapping("/visual/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateFromVisualDesign(@RequestBody Map<String, Object> designData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String reportName = (String) designData.get("reportName");
            if (reportName == null || reportName.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Report name is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (!reportName.endsWith(".jrxml")) {
                reportName += ".jrxml";
            }

            // Generate JRXML from visual design
            String jrxmlContent = generateJrxmlFromVisualDesign(designData);

            // Save to file
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(UPLOAD_DIR + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            logger.info("Successfully generated JRXML from visual design: {}", reportName);
            
            response.put("success", true);
            response.put("message", "Report generated successfully from visual design");
            response.put("reportName", reportName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error generating JRXML from visual design", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Helper method to generate JRXML from visual design data
     */
    private String generateJrxmlFromVisualDesign(Map<String, Object> designData) {
        StringBuilder jrxml = new StringBuilder();
        
        // Extract design properties
        @SuppressWarnings("unchecked")
        Map<String, Object> pageSettings = (Map<String, Object>) designData.getOrDefault("pageSettings", new HashMap<>());
        int pageWidth = ((Number) pageSettings.getOrDefault("width", 595)).intValue();
        int pageHeight = ((Number) pageSettings.getOrDefault("height", 842)).intValue();
        int leftMargin = ((Number) pageSettings.getOrDefault("leftMargin", 20)).intValue();
        int rightMargin = ((Number) pageSettings.getOrDefault("rightMargin", 20)).intValue();
        int topMargin = ((Number) pageSettings.getOrDefault("topMargin", 20)).intValue();
        int bottomMargin = ((Number) pageSettings.getOrDefault("bottomMargin", 20)).intValue();
        
        String reportName = (String) designData.getOrDefault("reportName", "VisualReport");
        String sqlQuery = (String) designData.get("sqlQuery");
        
        // Start JRXML
        jrxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        jrxml.append("<!DOCTYPE jasperReport PUBLIC \"-//JasperReports//DTD Report Design//EN\" \"http://jasperreports.sourceforge.net/dtds/jasperreport.dtd\">\n");
        jrxml.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n");
        jrxml.append("              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        jrxml.append("              xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"\n");
        jrxml.append(String.format("              name=\"%s\"\n", reportName));
        jrxml.append(String.format("              pageWidth=\"%d\"\n", pageWidth));
        jrxml.append(String.format("              pageHeight=\"%d\"\n", pageHeight));
        jrxml.append(String.format("              leftMargin=\"%d\"\n", leftMargin));
        jrxml.append(String.format("              rightMargin=\"%d\"\n", rightMargin));
        jrxml.append(String.format("              topMargin=\"%d\"\n", topMargin));
        jrxml.append(String.format("              bottomMargin=\"%d\"\n", bottomMargin));
        jrxml.append("              whenNoDataType=\"AllSectionsNoDetail\">\n\n");

        // Add fields from available fields or from elements
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) designData.get("fields");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) designData.getOrDefault("elements", new ArrayList<>());
        
        // Collect unique fields from elements
        java.util.Set<String> fieldNames = new java.util.HashSet<>();
        for (Map<String, Object> element : elements) {
            if ("field".equals(element.get("type"))) {
                String fieldName = (String) element.get("fieldName");
                if (fieldName != null && !fieldName.isEmpty()) {
                    fieldNames.add(fieldName);
                }
            }
        }
        
        // Generate field declarations
        if (fields != null && !fields.isEmpty()) {
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String fieldType = (String) field.get("type");
                if (fieldName != null && fieldNames.contains(fieldName)) {
                    jrxml.append(String.format("    <field name=\"%s\" class=\"%s\"/>\n",
                        fieldName,
                        mapSqlTypeToJavaClass(fieldType)));
                }
            }
        } else {
            // Generate fields from elements if not provided
            for (String fieldName : fieldNames) {
                Map<String, Object> fieldElement = elements.stream()
                    .filter(e -> "field".equals(e.get("type")) && fieldName.equals(e.get("fieldName")))
                    .findFirst()
                    .orElse(null);
                    
                String fieldType = "String";
                if (fieldElement != null && fieldElement.get("fieldType") != null) {
                    fieldType = (String) fieldElement.get("fieldType");
                }
                
                jrxml.append(String.format("    <field name=\"%s\" class=\"java.lang.%s\"/>\n",
                    fieldName, fieldType));
            }
        }
        
        jrxml.append("\n");
        
        // Add SQL query if present
        if (sqlQuery != null && !sqlQuery.trim().isEmpty()) {
            jrxml.append("    <queryString>\n");
            jrxml.append(String.format("        <![CDATA[%s]]>\n", sqlQuery));
            jrxml.append("    </queryString>\n\n");
        }
        
        // Group elements by band
        Map<String, List<Map<String, Object>>> bandElements = new HashMap<>();
        for (Map<String, Object> element : elements) {
            String band = (String) element.getOrDefault("band", "detail");
            bandElements.computeIfAbsent(band, k -> new ArrayList<>()).add(element);
        }

        // Generate bands
        String[] bands = {"title", "pageHeader", "columnHeader", "detail", "columnFooter", "pageFooter", "summary"};
        
        for (String band : bands) {
            List<Map<String, Object>> bandElems = bandElements.get(band);
            if (bandElems != null && !bandElems.isEmpty()) {
                // Calculate band height
                int maxY = 0;
                for (Map<String, Object> elem : bandElems) {
                    int y = ((Number) elem.getOrDefault("y", 0)).intValue();
                    int height = ((Number) elem.getOrDefault("height", 20)).intValue();
                    maxY = Math.max(maxY, y + height);
                }
                
                jrxml.append(String.format("    <%s>\n", band));
                jrxml.append(String.format("        <band height=\"%d\">\n", Math.max(maxY + 10, 50)));
                
                // Add elements
                for (Map<String, Object> elem : bandElems) {
                    jrxml.append(generateElementXml(elem));
                }
                
                jrxml.append("        </band>\n");
                jrxml.append(String.format("    </%s>\n", band));
            }
        }

        jrxml.append("</jasperReport>\n");
        
        return jrxml.toString();
    }
    
    /**
     * Map SQL type to Java class
     */
    private String mapSqlTypeToJavaClass(String sqlType) {
        if (sqlType == null) return "java.lang.String";
        
        String upperType = sqlType.toUpperCase();
        if (upperType.contains("INT")) {
            return "java.lang.Integer";
        } else if (upperType.contains("LONG") || upperType.contains("BIGINT")) {
            return "java.lang.Long";
        } else if (upperType.contains("DECIMAL") || upperType.contains("NUMERIC")) {
            return "java.math.BigDecimal";
        } else if (upperType.contains("DOUBLE") || upperType.contains("FLOAT")) {
            return "java.lang.Double";
        } else if (upperType.contains("DATE")) {
            return "java.sql.Date";
        } else if (upperType.contains("TIME")) {
            return "java.sql.Timestamp";
        } else if (upperType.contains("BOOL")) {
            return "java.lang.Boolean";
        } else {
            return "java.lang.String";
        }
    }

    /**
     * Generate XML for a single element
     */
    private String generateElementXml(Map<String, Object> element) {
        StringBuilder xml = new StringBuilder();
        String type = (String) element.get("type");
        int x = ((Number) element.getOrDefault("x", 0)).intValue();
        int y = ((Number) element.getOrDefault("y", 0)).intValue();
        int width = ((Number) element.getOrDefault("width", 100)).intValue();
        int height = ((Number) element.getOrDefault("height", 20)).intValue();
        
        switch (type) {
            case "text":
            case "staticText":
                String text = (String) element.getOrDefault("text", "");
                String fontName = (String) element.getOrDefault("fontName", "Arial");
                int fontSize = ((Number) element.getOrDefault("fontSize", 12)).intValue();
                boolean isBold = (boolean) element.getOrDefault("bold", false);
                boolean isItalic = (boolean) element.getOrDefault("italic", false);
                String alignment = (String) element.getOrDefault("alignment", "Left");
                String color = (String) element.getOrDefault("color", "#000000");
                
                xml.append("            <staticText>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <textElement");
                if (!alignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", alignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"", fontName, fontSize));
                if (isBold) xml.append(" isBold=\"true\"");
                if (isItalic) xml.append(" isItalic=\"true\"");
                xml.append("/>\n");
                xml.append("                </textElement>\n");
                xml.append(String.format("                <text><![CDATA[%s]]></text>\n", text));
                xml.append("            </staticText>\n");
                break;
                
            case "field":
                String fieldName = (String) element.getOrDefault("fieldName", "fieldName");
                String fieldFontName = (String) element.getOrDefault("fontName", "Arial");
                int fieldFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                boolean fieldBold = (boolean) element.getOrDefault("bold", false);
                boolean fieldItalic = (boolean) element.getOrDefault("italic", false);
                String fieldAlignment = (String) element.getOrDefault("alignment", "Left");
                String fieldColor = (String) element.getOrDefault("color", "#000000");
                String pattern = (String) element.getOrDefault("pattern", "");
                
                xml.append("            <textField");
                if (pattern != null && !pattern.isEmpty()) {
                    xml.append(String.format(" pattern=\"%s\"", pattern));
                }
                xml.append(">\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <textElement");
                if (!fieldAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", fieldAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"", fieldFontName, fieldFontSize));
                if (fieldBold) xml.append(" isBold=\"true\"");
                if (fieldItalic) xml.append(" isItalic=\"true\"");
                xml.append("/>\n");
                xml.append("                </textElement>\n");
                xml.append(String.format("                <textFieldExpression><![CDATA[$F{%s}]]></textFieldExpression>\n", fieldName));
                xml.append("            </textField>\n");
                break;
                
            case "pageNumber":
                String pageText = (String) element.getOrDefault("text", "Page ");
                String pageFontName = (String) element.getOrDefault("fontName", "Arial");
                int pageFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                String pageAlignment = (String) element.getOrDefault("alignment", "Right");
                
                xml.append("            <textField>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <textElement");
                if (!pageAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", pageAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"/>\n", pageFontName, pageFontSize));
                xml.append("                </textElement>\n");
                xml.append(String.format("                <textFieldExpression><![CDATA[\"%s\" + $V{PAGE_NUMBER}]]></textFieldExpression>\n", pageText));
                xml.append("            </textField>\n");
                break;
                
            case "currentDate":
                String datePattern = (String) element.getOrDefault("pattern", "dd/MM/yyyy");
                String dateFontName = (String) element.getOrDefault("fontName", "Arial");
                int dateFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                String dateAlignment = (String) element.getOrDefault("alignment", "Left");
                
                xml.append(String.format("            <textField pattern=\"%s\">\n", datePattern));
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <textElement");
                if (!dateAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", dateAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"/>\n", dateFontName, dateFontSize));
                xml.append("                </textElement>\n");
                xml.append("                <textFieldExpression><![CDATA[new java.util.Date()]]></textFieldExpression>\n");
                xml.append("            </textField>\n");
                break;
                
            case "image":
                String imagePath = (String) element.getOrDefault("imagePath", "");
                xml.append("            <image>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append(String.format("                <imageExpression><![CDATA[\"%s\"]]></imageExpression>\n", imagePath));
                xml.append("            </image>\n");
                break;
                
            case "line":
                xml.append("            <line>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("            </line>\n");
                break;
                
            case "rectangle":
                xml.append("            <rectangle>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("            </rectangle>\n");
                break;
        }
        
        return xml.toString();
    }
}

