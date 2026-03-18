package com.reportserver.controller;

import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.JrxmlBuilderService;
import com.reportserver.service.ReportService;
import com.reportserver.service.SchemaIntrospectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
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
    
    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;
    
    private static final String IMAGES_DIR = "data/images/";
    private static final String TEMPLATES_DIR = "data/templates/";

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private SchemaIntrospectionService schemaIntrospectionService;

    @Autowired
    private JrxmlBuilderService jrxmlBuilderService;

    @Autowired
    private ReportService reportService;

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
            @RequestParam(required = false) String variablesJson,
            @RequestParam(required = false) String reportOptionsJson) {
        
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

            // Parse shared cover options from JSON if provided
            Map<String, Object> reportOptions = new HashMap<>();
            if (reportOptionsJson != null && !reportOptionsJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    reportOptions = objectMapper.readValue(reportOptionsJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    logger.error("Error parsing reportOptions JSON", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid report options format: " + e.getMessage()));
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

            jrxmlContent = applySharedCoverToBuilderJrxml(jrxmlContent, reportName, reportOptions);

            // Save to file
            File uploadDir = new File(this.uploadDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(this.uploadDir + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            // Ensure next generation uses latest JRXML and not stale compiled cache.
            reportService.evictCompiledReport(this.uploadDir + reportName);
            reportService.evictCompiledReport(jrxmlFile.getAbsolutePath());

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

    private String applySharedCoverToBuilderJrxml(String jrxmlContent, String reportName, Map<String, Object> reportOptions) {
        if (jrxmlContent == null || jrxmlContent.isBlank() || reportOptions == null || reportOptions.isEmpty()) {
            return jrxmlContent;
        }

        boolean coverPageEnabled = asBoolean(reportOptions.get("coverPageEnabled"));
        if (!coverPageEnabled) {
            return jrxmlContent;
        }

        String coverTitle = asString(reportOptions.get("coverTitle"));
        String coverSubtitle = asString(reportOptions.get("coverSubtitle"));
        String coverAuthor = asString(reportOptions.get("coverAuthor"));
        boolean coverDateEnabled = asBoolean(reportOptions.get("coverDateEnabled"));
        boolean coverIncludeReportName = !reportOptions.containsKey("coverIncludeReportName")
            || asBoolean(reportOptions.get("coverIncludeReportName"));
        String coverDatePattern = asString(reportOptions.get("coverDatePattern"));
        String coverAlignment = normalizeHorizontalAlignment(asString(reportOptions.get("coverAlignment")));
        String coverTheme = asString(reportOptions.get("coverTheme"));
        if (coverTheme.isBlank()) {
            coverTheme = "classicBlue";
        }
        boolean coverAccentEnabled = !reportOptions.containsKey("coverAccentEnabled")
            || asBoolean(reportOptions.get("coverAccentEnabled"));
        boolean coverBackgroundShapeEnabled = !reportOptions.containsKey("coverBackgroundShapeEnabled")
            || asBoolean(reportOptions.get("coverBackgroundShapeEnabled"));
        int coverTitleSize = asInt(reportOptions.get("coverTitleSize"), 30, 12, 72);
        int coverSubtitleSize = asInt(reportOptions.get("coverSubtitleSize"), 16, 10, 48);
        String coverLogoData = asString(reportOptions.get("coverLogoData"));
        String coverPageFileData = asString(reportOptions.get("coverPageFileData"));

        Map<String, List<Map<String, Object>>> coverBands = new HashMap<>();
        injectCoverPageElements(
            coverBands,
            reportName,
            coverTitle,
            coverSubtitle,
            coverAuthor,
            coverIncludeReportName,
            coverDateEnabled,
            coverDatePattern,
            coverAlignment,
            coverTheme,
            coverAccentEnabled,
            coverBackgroundShapeEnabled,
            coverTitleSize,
            coverSubtitleSize,
            coverLogoData,
            coverPageFileData,
            595,
            20,
            802,
            true);

        List<Map<String, Object>> titleElements = coverBands.get("title");
        if (titleElements == null || titleElements.isEmpty()) {
            return jrxmlContent;
        }

        int maxBottom = titleElements.stream()
            .mapToInt(element -> ((Number) element.getOrDefault("y", 0)).intValue()
                + Math.max(1, ((Number) element.getOrDefault("height", 20)).intValue()))
            .max()
            .orElse(555);
        int titleBandHeight = Math.max(595, maxBottom + 20);

        StringBuilder titleXml = new StringBuilder();
        titleXml.append("\t<title>\n");
        titleXml.append(String.format("\t\t<band height=\"%d\">\n", titleBandHeight));
        for (Map<String, Object> element : titleElements) {
            titleXml.append(generateElementXml(element, "title"));
        }
        titleXml.append("\t\t</band>\n");
        titleXml.append("\t</title>\n");

        return replaceTitleBand(jrxmlContent, titleXml.toString());
    }

    private String replaceTitleBand(String jrxmlContent, String titleBandXml) {
        int titleStart = jrxmlContent.indexOf("\t<title>");
        if (titleStart >= 0) {
            int titleEnd = jrxmlContent.indexOf("\t</title>", titleStart);
            if (titleEnd >= 0) {
                int replaceEnd = titleEnd + "\t</title>".length();
                if (replaceEnd < jrxmlContent.length() && jrxmlContent.charAt(replaceEnd) == '\r') {
                    replaceEnd++;
                }
                if (replaceEnd < jrxmlContent.length() && jrxmlContent.charAt(replaceEnd) == '\n') {
                    replaceEnd++;
                }
                return jrxmlContent.substring(0, titleStart) + titleBandXml + jrxmlContent.substring(replaceEnd);
            }
        }

        int insertIndex = jrxmlContent.indexOf("\t<columnHeader>");
        if (insertIndex < 0) {
            insertIndex = jrxmlContent.indexOf("\t<detail>");
        }
        if (insertIndex < 0) {
            return jrxmlContent;
        }

        return jrxmlContent.substring(0, insertIndex) + titleBandXml + jrxmlContent.substring(insertIndex);
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

            File file = new File(this.uploadDir + fileName);
            
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
     * Upload a cover page file and return image data that can be previewed and injected in JRXML.
     * Supported formats: PDF (first page), PNG, JPG, JPEG, WEBP, GIF, BMP.
     */
    @PostMapping("/upload-cover-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadCoverFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            if (file.getSize() > (20L * 1024L * 1024L)) {
                response.put("success", false);
                response.put("message", "File is too large. Maximum allowed size is 20 MB");
                return ResponseEntity.badRequest().body(response);
            }

            String originalFilename = file.getOriginalFilename();
            String filenameLower = originalFilename == null ? "" : originalFilename.toLowerCase();
            String contentType = file.getContentType();
            String contentTypeLower = contentType == null ? "" : contentType.toLowerCase();

            boolean isPdf = "application/pdf".equals(contentTypeLower) || filenameLower.endsWith(".pdf");
            boolean isImage = contentTypeLower.startsWith("image/")
                    || filenameLower.matches(".*\\.(png|jpg|jpeg|webp|gif|bmp)$");

            if (!isPdf && !isImage) {
                response.put("success", false);
                response.put("message", "Unsupported file format. Supported: PDF, PNG, JPG, JPEG, WEBP, GIF, BMP");
                return ResponseEntity.badRequest().body(response);
            }

            String coverImageData;
            boolean convertedFromPdf = false;

            if (isPdf) {
                byte[] pdfBytes = file.getBytes();
                try (PDDocument document = PDDocument.load(pdfBytes);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                    if (document.getNumberOfPages() == 0) {
                        response.put("success", false);
                        response.put("message", "Uploaded PDF has no pages");
                        return ResponseEntity.badRequest().body(response);
                    }

                    PDFRenderer renderer = new PDFRenderer(document);
                    BufferedImage firstPage = renderer.renderImageWithDPI(0, 150);
                    ImageIO.write(firstPage, "png", out);

                    coverImageData = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
                    convertedFromPdf = true;
                }
            } else {
                String imageMimeType = resolveCoverImageMimeType(contentTypeLower, filenameLower);
                coverImageData = "data:" + imageMimeType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
            }

            response.put("success", true);
            response.put("message", convertedFromPdf
                    ? "PDF uploaded and first page converted successfully"
                    : "Cover image uploaded successfully");
            response.put("coverImageData", coverImageData);
            response.put("convertedFromPdf", convertedFromPdf);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading cover file", e);
            response.put("success", false);
            response.put("message", "Error uploading cover file: " + e.getMessage());
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
            File uploadDir = new File(this.uploadDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(this.uploadDir + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            // Ensure next generation uses latest JRXML and not stale compiled cache.
            reportService.evictCompiledReport(this.uploadDir + reportName);
            reportService.evictCompiledReport(jrxmlFile.getAbsolutePath());

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
        int printableWidth = Math.max(1, pageWidth - leftMargin - rightMargin);
        String orientation = String.valueOf(pageSettings.getOrDefault("orientation", pageWidth > pageHeight ? "Landscape" : "Portrait"));
        
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
        jrxml.append(String.format("              orientation=\"%s\"\n", orientation));
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
        @SuppressWarnings("unchecked")
        Map<String, Object> reportOptions = (Map<String, Object>) designData.getOrDefault("reportOptions", new HashMap<>());

        boolean headerFirstPageOnly = asBoolean(reportOptions.get("headerFirstPageOnly"));
        boolean coverPageEnabled = asBoolean(reportOptions.get("coverPageEnabled"));
        String coverTitle = asString(reportOptions.get("coverTitle"));
        String coverSubtitle = asString(reportOptions.get("coverSubtitle"));
        String coverAuthor = asString(reportOptions.get("coverAuthor"));
        boolean coverDateEnabled = asBoolean(reportOptions.get("coverDateEnabled"));
        boolean coverIncludeReportName = !reportOptions.containsKey("coverIncludeReportName")
            || asBoolean(reportOptions.get("coverIncludeReportName"));
        String coverDatePattern = asString(reportOptions.get("coverDatePattern"));
        String coverAlignment = normalizeHorizontalAlignment(asString(reportOptions.get("coverAlignment")));
        String coverTheme = asString(reportOptions.get("coverTheme"));
        if (coverTheme.isBlank()) {
            coverTheme = "classicBlue";
        }
        boolean coverAccentEnabled = !reportOptions.containsKey("coverAccentEnabled")
            || asBoolean(reportOptions.get("coverAccentEnabled"));
        boolean coverBackgroundShapeEnabled = !reportOptions.containsKey("coverBackgroundShapeEnabled")
            || asBoolean(reportOptions.get("coverBackgroundShapeEnabled"));
        int coverTitleSize = asInt(reportOptions.get("coverTitleSize"), 30, 12, 72);
        int coverSubtitleSize = asInt(reportOptions.get("coverSubtitleSize"), 16, 10, 48);
        String coverLogoData = asString(reportOptions.get("coverLogoData"));
        String coverPageFileData = asString(reportOptions.get("coverPageFileData"));

        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            sqlQuery = inferSqlQueryFromElements(elements);
        }
        if (fields == null || fields.isEmpty()) {
            fields = inferFieldsFromElements(elements);
        }
        
        // Add SQL query if present
        if (sqlQuery != null && !sqlQuery.trim().isEmpty()) {
            jrxml.append("    <queryString>\n");
            jrxml.append(String.format("        <![CDATA[%s]]>\n", sqlQuery));
            jrxml.append("    </queryString>\n\n");
        }

        // Collect unique fields from elements
        java.util.Set<String> fieldNames = new java.util.HashSet<>();
        for (Map<String, Object> element : elements) {
            if ("field".equals(element.get("type"))) {
                String fieldName = (String) element.get("fieldName");
                if (fieldName != null && !fieldName.isEmpty()) {
                    fieldNames.add(fieldName);
                }
            }
            if ("dbTable".equals(element.get("type"))) {
                for (String columnName : getSelectedColumns(element)) {
                    fieldNames.add(columnName);
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
                        resolveFieldClass(fieldType)));
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
        
        // Group elements by band
        Map<String, List<Map<String, Object>>> bandElements = new HashMap<>();
        for (Map<String, Object> element : elements) {
            String band = (String) element.getOrDefault("band", "detail");
            // Keep compatibility with older designs and map top-zone semantics.
            if ("title".equals(band) || "pageHeader".equals(band)) {
                band = headerFirstPageOnly ? "title" : "pageHeader";
            }
            bandElements.computeIfAbsent(band, k -> new ArrayList<>()).add(element);
        }

        if (coverPageEnabled) {
            boolean hasDataOrCanvasContent = (sqlQuery != null && !sqlQuery.trim().isEmpty()) || !elements.isEmpty();
            injectCoverPageElements(
                    bandElements,
                    reportName,
                    coverTitle,
                    coverSubtitle,
                    coverAuthor,
                    coverIncludeReportName,
                    coverDateEnabled,
                    coverDatePattern,
                    coverAlignment,
                    coverTheme,
                    coverAccentEnabled,
                    coverBackgroundShapeEnabled,
                    coverTitleSize,
                    coverSubtitleSize,
                    coverLogoData,
                    coverPageFileData,
                    pageHeight,
                    topMargin,
                    printableWidth,
                    hasDataOrCanvasContent);
        }

        // Database tables are authored visually, but JRXML must print table headers once
        // per page (pageHeader) and data rows repeatedly (detail), regardless of drop zone.
        List<Map<String, Object>> detailDbTables = elements.stream()
            .filter(element -> "dbTable".equals(element.get("type")))
            .map(HashMap::new)
            .collect(Collectors.toList());

        if (!detailDbTables.isEmpty()) {
            // Remove dbTable from all original bands. We'll place it explicitly in
            // pageHeader (header) and detail (rows).
            for (List<Map<String, Object>> bandList : bandElements.values()) {
                bandList.removeIf(element -> "dbTable".equals(element.get("type")));
            }

            List<Map<String, Object>> rawDetailElements = new ArrayList<>(bandElements.getOrDefault("detail", new ArrayList<>()));
            List<Map<String, Object>> detailRowElements = new ArrayList<>();
            List<Map<String, Object>> detailStaticElements = new ArrayList<>();
            List<Map<String, Object>> detailFooterElements = new ArrayList<>();

            detailRowElements.addAll(detailDbTables);

            for (Map<String, Object> element : rawDetailElements) {
                String type = String.valueOf(element.get("type"));

                if ("field".equals(type)) {
                    detailRowElements.add(element);
                } else if ("pageNumber".equals(type)) {
                    detailFooterElements.add(element);
                } else {
                    detailStaticElements.add(element);
                }
            }

            bandElements.put("detail", detailRowElements);

            if (!detailStaticElements.isEmpty()) {
                String staticHeaderBand = headerFirstPageOnly ? "title" : "pageHeader";
                bandElements.computeIfAbsent(staticHeaderBand, key -> new ArrayList<>()).addAll(detailStaticElements);
            }
            if (!detailFooterElements.isEmpty()) {
                bandElements.computeIfAbsent("pageFooter", key -> new ArrayList<>()).addAll(detailFooterElements);
            }
        }

        // Generate bands
        String[] bands = {"title", "pageHeader", "columnHeader", "detail", "columnFooter", "pageFooter", "summary"};
        
        for (String band : bands) {
            List<Map<String, Object>> bandElems = new ArrayList<>();
            if (bandElements.containsKey(band)) {
                bandElems.addAll(bandElements.get(band));
            }
            if ("pageHeader".equals(band) && !detailDbTables.isEmpty()) {
                bandElems.addAll(detailDbTables);
            }

            if (bandElems != null && !bandElems.isEmpty()) {
                int dbTableHeaderYOffset = 0;
                if ("pageHeader".equals(band) && !detailDbTables.isEmpty()) {
                    int staticHeaderBottom = 0;
                    for (Map<String, Object> elem : bandElems) {
                        if ("dbTable".equals(elem.get("type"))) {
                            continue;
                        }
                        int elemY = ((Number) elem.getOrDefault("y", 0)).intValue();
                        int elemHeight = getEffectiveElementHeight(elem, band);
                        staticHeaderBottom = Math.max(staticHeaderBottom, elemY + elemHeight);
                    }
                    dbTableHeaderYOffset = staticHeaderBottom > 0 ? staticHeaderBottom + 8 : 0;
                }

                // Calculate band height and normalize Y coordinates per band.
                // Canvas coordinates are global (Header/Body/Footer zones), while JRXML
                // band coordinates are local to each band.
                int minY = Integer.MAX_VALUE;
                int maxBottom = 0;
                for (Map<String, Object> elem : bandElems) {
                    int y = ((Number) elem.getOrDefault("y", 0)).intValue();
                    if ("dbTable".equals(elem.get("type")) && "pageHeader".equals(band)) {
                        y = dbTableHeaderYOffset;
                    } else if ("dbTable".equals(elem.get("type")) && "detail".equals(band)) {
                        y = 0;
                    } else if (!detailDbTables.isEmpty() && "detail".equals(band) && "field".equals(elem.get("type"))) {
                        y = 0;
                    }

                    int effectiveHeight = getEffectiveElementHeight(elem, band);
                    minY = Math.min(minY, y);
                    maxBottom = Math.max(maxBottom, y + effectiveHeight);
                }

                if (minY == Integer.MAX_VALUE) {
                    minY = 0;
                }
                int bandHeight = Math.max((maxBottom - minY) + 8, 24);
                
                jrxml.append(String.format("    <%s>\n", band));
                jrxml.append(String.format("        <band height=\"%d\">\n", bandHeight));
                
                // Add elements
                for (Map<String, Object> elem : bandElems) {
                    Map<String, Object> relativeElem = new HashMap<>(elem);
                    int originalY = ((Number) elem.getOrDefault("y", 0)).intValue();

                    if ("dbTable".equals(elem.get("type")) && "pageHeader".equals(band)) {
                        relativeElem.put("y", Math.max(0, dbTableHeaderYOffset - minY));
                    } else if ("dbTable".equals(elem.get("type")) && "detail".equals(band)) {
                        relativeElem.put("y", 0);
                    } else if (!detailDbTables.isEmpty() && "detail".equals(band) && "field".equals(elem.get("type"))) {
                        relativeElem.put("y", 0);
                    } else {
                        relativeElem.put("y", Math.max(0, originalY - minY));
                    }

                    clampElementToPrintableWidth(relativeElem, printableWidth);

                    jrxml.append(generateElementXml(relativeElem, band));
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

    private String resolveCoverImageMimeType(String contentTypeLower, String filenameLower) {
        if (contentTypeLower != null && contentTypeLower.startsWith("image/")) {
            return contentTypeLower;
        }
        if (filenameLower.endsWith(".png")) return "image/png";
        if (filenameLower.endsWith(".jpg") || filenameLower.endsWith(".jpeg")) return "image/jpeg";
        if (filenameLower.endsWith(".webp")) return "image/webp";
        if (filenameLower.endsWith(".gif")) return "image/gif";
        if (filenameLower.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private String resolveFieldClass(String fieldType) {
        if (fieldType == null || fieldType.isBlank()) {
            return "java.lang.String";
        }
        if (fieldType.startsWith("java.")) {
            return fieldType;
        }
        return mapSqlTypeToJavaClass(fieldType);
    }

    @SuppressWarnings("unchecked")
    private String inferSqlQueryFromElements(List<Map<String, Object>> elements) {
        if (elements == null) {
            return null;
        }

        for (Map<String, Object> element : elements) {
            if (!"dbTable".equals(element.get("type"))) {
                continue;
            }

            String tableName = (String) element.get("tableName");
            List<String> selectedColumns = getSelectedColumns(element);
            if (tableName == null || tableName.isBlank() || selectedColumns.isEmpty()) {
                continue;
            }

            String columns = selectedColumns.stream()
                    .map(column -> "`" + column + "`")
                    .collect(Collectors.joining(", "));
            return "SELECT " + columns + " FROM `" + tableName + "`";
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> inferFieldsFromElements(List<Map<String, Object>> elements) {
        Map<String, Map<String, Object>> inferred = new java.util.LinkedHashMap<>();
        if (elements == null) {
            return new ArrayList<>();
        }

        for (Map<String, Object> element : elements) {
            if (!"dbTable".equals(element.get("type"))) {
                continue;
            }

            List<String> selectedColumns = getSelectedColumns(element);
            List<Map<String, Object>> columnDetails = (List<Map<String, Object>>) element.get("columnDetails");

            for (String columnName : selectedColumns) {
                String javaClass = "java.lang.String";
                if (columnDetails != null) {
                    for (Map<String, Object> detail : columnDetails) {
                        if (columnName.equals(String.valueOf(detail.get("name")))) {
                            Object explicitJavaClass = detail.get("javaClass");
                            Object fallbackType = detail.get("type");
                            if (explicitJavaClass != null && !String.valueOf(explicitJavaClass).isBlank()) {
                                javaClass = String.valueOf(explicitJavaClass);
                            } else if (fallbackType != null) {
                                javaClass = mapSqlTypeToJavaClass(String.valueOf(fallbackType));
                            }
                            break;
                        }
                    }
                }

                Map<String, Object> field = new HashMap<>();
                field.put("name", columnName);
                field.put("type", javaClass);
                inferred.put(columnName, field);
            }
        }

        return new ArrayList<>(inferred.values());
    }

    @SuppressWarnings("unchecked")
    private List<String> getSelectedColumns(Map<String, Object> element) {
        Object selectedColumnsObj = element.get("selectedColumns");
        if (selectedColumnsObj instanceof List<?> selectedColumns && !selectedColumns.isEmpty()) {
            return selectedColumns.stream().map(String::valueOf).collect(Collectors.toList());
        }

        Object columnsObj = element.get("columns");
        if (columnsObj instanceof List<?> columns) {
            return columns.stream().map(String::valueOf).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number numberValue) {
            parsed = numberValue.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static final class CoverThemePalette {
        private final String titleColor;
        private final String subtitleColor;
        private final String metaColor;
        private final String accentColor;
        private final String backgroundColor;

        private CoverThemePalette(String titleColor, String subtitleColor, String metaColor, String accentColor, String backgroundColor) {
            this.titleColor = titleColor;
            this.subtitleColor = subtitleColor;
            this.metaColor = metaColor;
            this.accentColor = accentColor;
            this.backgroundColor = backgroundColor;
        }
    }

    private String normalizeHorizontalAlignment(String value) {
        if (value == null) {
            return "Center";
        }
        String normalized = value.trim().toLowerCase();
        if ("left".equals(normalized)) {
            return "Left";
        }
        if ("right".equals(normalized)) {
            return "Right";
        }
        return "Center";
    }

    private CoverThemePalette resolveCoverThemePalette(String coverTheme) {
        String normalized = coverTheme == null ? "" : coverTheme.trim().toLowerCase();
        switch (normalized) {
            case "forest":
                return new CoverThemePalette("#1F4A38", "#2E6A52", "#4C7564", "#2F8F6D", "#EAF7F1");
            case "sunrise":
                return new CoverThemePalette("#6A3A1B", "#9A5728", "#A16C47", "#E07B39", "#FFF2E8");
            case "charcoal":
                return new CoverThemePalette("#2A2F36", "#3E4650", "#59626D", "#8C99A8", "#F2F5F8");
            case "midnightgold":
                return new CoverThemePalette("#2A2A3D", "#4A4762", "#666375", "#B08A2E", "#F6F1E3");
            case "classicblue":
            default:
                return new CoverThemePalette("#143A62", "#2F5C8A", "#486A8E", "#2E75B6", "#EAF2FB");
        }
    }

    private void injectCoverPageElements(
            Map<String, List<Map<String, Object>>> bandElements,
            String reportName,
            String coverTitle,
            String coverSubtitle,
            String coverAuthor,
            boolean coverIncludeReportName,
            boolean coverDateEnabled,
            String coverDatePattern,
            String coverAlignment,
            String coverTheme,
            boolean coverAccentEnabled,
            boolean coverBackgroundShapeEnabled,
            int coverTitleSize,
            int coverSubtitleSize,
            String coverLogoData,
            String coverPageFileData,
            int pageHeight,
            int topMargin,
            int printableWidth,
            boolean appendPageBreak) {

        String effectiveTitle = coverTitle;
        if ((effectiveTitle == null || effectiveTitle.isBlank()) && coverIncludeReportName) {
            effectiveTitle = reportName;
        }
        if (effectiveTitle != null && effectiveTitle.toLowerCase().endsWith(".jrxml")) {
            effectiveTitle = effectiveTitle.substring(0, effectiveTitle.length() - 6);
        }

        List<Map<String, Object>> titleBand = bandElements.computeIfAbsent("title", key -> new ArrayList<>());
        CoverThemePalette palette = resolveCoverThemePalette(coverTheme);

        int safePrintableWidth = Math.max(140, printableWidth);
        int titleY = Math.max(44, (pageHeight / 3) - topMargin);
        int subtitleY = titleY + Math.max(42, coverTitleSize + 12);
        int metaY = subtitleY + Math.max(36, coverSubtitleSize + 16);

        if (coverPageFileData != null && !coverPageFileData.isBlank()) {
            int coverImageHeight = Math.max(240, pageHeight - (topMargin * 2));
            titleBand.add(createCoverBackgroundImageElement(0, 0, safePrintableWidth, coverImageHeight, coverPageFileData));
        }

        if (coverBackgroundShapeEnabled) {
            int shapeHeight = Math.max(180, Math.min(280, pageHeight / 2));
            titleBand.add(createCoverBackgroundShape(0, 0, safePrintableWidth, shapeHeight, palette.backgroundColor));
        }

        if (coverLogoData != null && !coverLogoData.isBlank()) {
            int logoWidth = Math.max(120, Math.min(260, safePrintableWidth / 3));
            int logoX;
            if ("Left".equals(coverAlignment)) {
                logoX = 0;
            } else if ("Right".equals(coverAlignment)) {
                logoX = Math.max(0, safePrintableWidth - logoWidth);
            } else {
                logoX = (safePrintableWidth - logoWidth) / 2;
            }
            int logoY = Math.max(24, titleY - 112);
            titleBand.add(createCoverLogoElement(logoX, logoY, logoWidth, 80, coverLogoData));
        }

        if (effectiveTitle != null && !effectiveTitle.isBlank()) {
            titleBand.add(createCoverTextElement(0, titleY, safePrintableWidth, Math.max(36, coverTitleSize + 10), effectiveTitle, coverTitleSize, true, coverAlignment, palette.titleColor));
        }
        if (coverSubtitle != null && !coverSubtitle.isBlank()) {
            titleBand.add(createCoverTextElement(0, subtitleY, safePrintableWidth, Math.max(24, coverSubtitleSize + 8), coverSubtitle, coverSubtitleSize, false, coverAlignment, palette.subtitleColor));
        }

        if (coverAuthor != null && !coverAuthor.isBlank()) {
            titleBand.add(createCoverTextElement(0, metaY, safePrintableWidth, 22, coverAuthor, 12, false, coverAlignment, palette.metaColor));
            metaY += 28;
        }

        if (coverDateEnabled) {
            String effectivePattern = (coverDatePattern == null || coverDatePattern.isBlank()) ? "dd/MM/yyyy" : coverDatePattern;
            titleBand.add(createCoverDateElement(0, metaY, safePrintableWidth, 22, effectivePattern, coverAlignment, palette.metaColor));
            metaY += 28;
        }

        if (coverAccentEnabled) {
            titleBand.add(createCoverAccentLine(0, metaY + 2, safePrintableWidth, palette.accentColor));
        }

        titleBand.removeIf(element -> "pageBreak".equals(element.get("type")));

        int coverBottom = titleBand.stream()
                .filter(element -> !"pageBreak".equals(element.get("type")))
                .mapToInt(element -> ((Number) element.getOrDefault("y", 0)).intValue()
                        + ((Number) element.getOrDefault("height", 20)).intValue())
                .max()
                .orElse(titleY + 120);

        if (appendPageBreak) {
            titleBand.add(createPageBreakElement(Math.max(coverBottom + 20, 120), safePrintableWidth));
        }
    }

    private Map<String, Object> createCoverTextElement(
            int x,
            int y,
            int width,
            int height,
            String text,
            int fontSize,
            boolean bold,
            String alignment,
            String color) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "staticText");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("text", text == null ? "" : text);
        element.put("fontName", "DejaVu Sans");
        element.put("fontSize", fontSize);
        element.put("bold", bold);
        element.put("italic", false);
        element.put("alignment", alignment);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createPageBreakElement(int y, int width) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "pageBreak");
        element.put("x", 0);
        element.put("y", Math.max(0, y));
        element.put("width", Math.max(1, width));
        element.put("height", 1);
        return element;
    }

    private Map<String, Object> createCoverDateElement(
            int x,
            int y,
            int width,
            int height,
            String pattern,
            String alignment,
            String color) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "date");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("pattern", pattern);
        element.put("fontName", "DejaVu Sans");
        element.put("fontSize", 12);
        element.put("alignment", alignment);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createCoverBackgroundShape(
            int x,
            int y,
            int width,
            int height,
            String backgroundColor) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "rectangle");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("backgroundTransparent", false);
        element.put("backgroundColor", backgroundColor);
        element.put("borderColor", backgroundColor);
        element.put("borderWidth", 0);
        return element;
    }

    private Map<String, Object> createCoverBackgroundImageElement(
            int x,
            int y,
            int width,
            int height,
            String imageData) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "image");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("imageData", imageData);
        return element;
    }

    private Map<String, Object> createCoverAccentLine(int x, int y, int width, String color) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "line");
        element.put("x", x);
        element.put("y", Math.max(0, y));
        element.put("width", Math.max(1, width));
        element.put("height", 2);
        element.put("borderWidth", 1);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createCoverLogoElement(
            int x,
            int y,
            int width,
            int height,
            String imageData) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "image");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("imageData", imageData);
        return element;
    }

    private int getEffectiveElementHeight(Map<String, Object> element, String band) {
        String type = (String) element.get("type");
        int configuredHeight = ((Number) element.getOrDefault("height", 20)).intValue();

        if ("pageBreak".equals(type)) {
            return Math.max(1, configuredHeight);
        }

        if ("dbTable".equals(type)) {
            if ("pageHeader".equals(band) || "columnHeader".equals(band)) {
                return getDbTableHeaderHeight(configuredHeight);
            }
            if ("detail".equals(band)) {
                return getDbTableRowHeight(configuredHeight);
            }
            return getDbTableHeaderHeight(configuredHeight) + getDbTableRowHeight(configuredHeight);
        }

        return configuredHeight;
    }

    private int getDbTableHeaderHeight(int configuredHeight) {
        return Math.min(24, Math.max(18, configuredHeight / 6));
    }

    private int getDbTableRowHeight(int configuredHeight) {
        return Math.min(24, Math.max(18, configuredHeight / 6));
    }

    private void clampElementToPrintableWidth(Map<String, Object> element, int printableWidth) {
        int x = ((Number) element.getOrDefault("x", 0)).intValue();
        int width = ((Number) element.getOrDefault("width", 100)).intValue();

        x = Math.max(0, x);
        width = Math.max(1, width);

        if (x >= printableWidth) {
            x = 0;
        }
        if (x + width > printableWidth) {
            width = Math.max(1, printableWidth - x);
        }

        element.put("x", x);
        element.put("width", width);
    }

    private String normalizeHexColor(String color, String fallback) {
        String effectiveFallback = (fallback == null || fallback.isBlank()) ? "#000000" : fallback;
        if (color == null || color.isBlank()) {
            return effectiveFallback;
        }

        String normalized = color.trim();
        if (normalized.matches("#[0-9a-fA-F]{6}")) {
            return normalized.toUpperCase();
        }
        if (normalized.matches("#[0-9a-fA-F]{3}")) {
            char r = normalized.charAt(1);
            char g = normalized.charAt(2);
            char b = normalized.charAt(3);
            return ("#" + r + r + g + g + b + b).toUpperCase();
        }
        return effectiveFallback;
    }

    /**
     * Generate XML for a single element
     */
    private String generateElementXml(Map<String, Object> element, String band) {
        StringBuilder xml = new StringBuilder();
        String type = (String) element.get("type");
        int x = ((Number) element.getOrDefault("x", 0)).intValue();
        int y = ((Number) element.getOrDefault("y", 0)).intValue();
        int width = ((Number) element.getOrDefault("width", 100)).intValue();
        int height = ((Number) element.getOrDefault("height", 20)).intValue();
        
        switch (type) {
            case "text":
            case "label":
            case "staticText":
                String text = (String) element.getOrDefault("text", "");
                String fontName = (String) element.getOrDefault("fontName", "Arial");
                int fontSize = ((Number) element.getOrDefault("fontSize", 12)).intValue();
                boolean isBold = (boolean) element.getOrDefault("bold", false);
                boolean isItalic = (boolean) element.getOrDefault("italic", false);
                String alignment = (String) element.getOrDefault("alignment", "Left");
                String color = (String) element.getOrDefault("color", "#000000");
                String textColor = normalizeHexColor(color, "#000000");
                
                xml.append("            <staticText>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, textColor));
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
                String safeFieldColor = normalizeHexColor(fieldColor, "#000000");
                String pattern = (String) element.getOrDefault("pattern", "");
                
                xml.append("            <textField");
                if (pattern != null && !pattern.isEmpty()) {
                    xml.append(String.format(" pattern=\"%s\"", pattern));
                }
                xml.append(">\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, safeFieldColor));
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
                String pageColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");
                
                xml.append("            <textField>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, pageColor));
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
                
            case "date":
            case "currentDate":
                String datePattern = (String) element.getOrDefault("pattern", "dd/MM/yyyy");
                String dateFontName = (String) element.getOrDefault("fontName", "Arial");
                int dateFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                String dateAlignment = (String) element.getOrDefault("alignment", "Left");
                String dateColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");
                
                xml.append(String.format("            <textField pattern=\"%s\">\n", datePattern));
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, dateColor));
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
                
            case "logo":
            case "image":
                String imageData = (String) element.getOrDefault("imageData", element.getOrDefault("imagePath", ""));
                if (imageData != null && !imageData.isBlank()) {
                    String base64 = imageData.contains(",") ? imageData.substring(imageData.indexOf(',') + 1) : imageData;
                    xml.append("            <image scaleImage=\"RetainShape\">\n");
                    xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                    xml.append(String.format("                <imageExpression><![CDATA[new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(\"%s\"))]]></imageExpression>\n", base64));
                    xml.append("            </image>\n");
                }
                break;

            case "dbTable":
                if ("pageHeader".equals(band) || "columnHeader".equals(band)) {
                    xml.append(generateDbTableHeaderXml(element, x, y, width, height));
                } else {
                    xml.append(generateDbTableDetailXml(element, x, y, width, height));
                }
                break;
                
            case "line":
                String lineColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");
                int lineWidth = Math.max(1, ((Number) element.getOrDefault("borderWidth", 1)).intValue());
                xml.append("            <line>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <graphicElement>\n");
                xml.append(String.format("                    <pen lineWidth=\"%d\" lineColor=\"%s\"/>\n", lineWidth, lineColor));
                xml.append("                </graphicElement>\n");
                xml.append("            </line>\n");
                break;
                
            case "rectangle":
                String rectangleBorderColor = normalizeHexColor((String) element.getOrDefault("borderColor", "#000000"), "#000000");
                String rectangleBackgroundColor = normalizeHexColor((String) element.getOrDefault("backgroundColor", "#FFFFFF"), "#FFFFFF");
                int rectangleBorderWidth = Math.max(0, ((Number) element.getOrDefault("borderWidth", 1)).intValue());
                boolean rectangleTransparent = !element.containsKey("backgroundTransparent")
                        || asBoolean(element.get("backgroundTransparent"));

                xml.append("            <rectangle>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"", x, y, width, height));
                if (!rectangleTransparent) {
                    xml.append(String.format(" mode=\"Opaque\" backcolor=\"%s\"", rectangleBackgroundColor));
                }
                xml.append("/>\n");
                xml.append("                <graphicElement>\n");
                xml.append(String.format("                    <pen lineWidth=\"%d\" lineColor=\"%s\"/>\n", rectangleBorderWidth, rectangleBorderColor));
                xml.append("                </graphicElement>\n");
                xml.append("            </rectangle>\n");
                break;

            case "pageBreak":
                xml.append("            <break>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"1\"/>\n", x, y, Math.max(1, width)));
                xml.append("            </break>\n");
                break;
        }
        
        return xml.toString();
    }

    private String generateDbTableHeaderXml(Map<String, Object> element, int x, int y, int width, int height) {
        StringBuilder xml = new StringBuilder();
        List<String> selectedColumns = getSelectedColumns(element);
        if (selectedColumns.isEmpty()) {
            return "";
        }

        int columnCount = Math.max(1, selectedColumns.size());
        int columnWidth = Math.max(60, width / columnCount);
        int headerHeight = getDbTableHeaderHeight(height);

        for (int i = 0; i < selectedColumns.size(); i++) {
            String column = selectedColumns.get(i);
            int columnX = x + (i * columnWidth);
            int effectiveWidth = (i == selectedColumns.size() - 1) ? Math.max(60, width - (i * columnWidth)) : columnWidth;

            xml.append("            <staticText>\n");
            xml.append(String.format("                <reportElement mode=\"Opaque\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" backcolor=\"#DDE6F5\"/>\n", columnX, y, effectiveWidth, headerHeight));
            xml.append("                <box><pen lineWidth=\"1.0\"/></box>\n");
            xml.append("                <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
            xml.append("                    <font size=\"10\" isBold=\"true\"/>\n");
            xml.append("                </textElement>\n");
            xml.append(String.format("                <text><![CDATA[%s]]></text>\n", column));
            xml.append("            </staticText>\n");
        }

        return xml.toString();
    }

    private String generateDbTableDetailXml(Map<String, Object> element, int x, int y, int width, int height) {
        StringBuilder xml = new StringBuilder();
        List<String> selectedColumns = getSelectedColumns(element);
        if (selectedColumns.isEmpty()) {
            return "";
        }

        int columnCount = Math.max(1, selectedColumns.size());
        int columnWidth = Math.max(60, width / columnCount);
        int rowHeight = getDbTableRowHeight(height);

        for (int i = 0; i < selectedColumns.size(); i++) {
            String column = selectedColumns.get(i);
            int columnX = x + (i * columnWidth);
            int effectiveWidth = (i == selectedColumns.size() - 1) ? Math.max(60, width - (i * columnWidth)) : columnWidth;

            xml.append("            <textField isStretchWithOverflow=\"true\">\n");
            xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", columnX, y, effectiveWidth, rowHeight));
            xml.append("                <box><pen lineWidth=\"1.0\"/></box>\n");
            xml.append("                <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
            xml.append("                    <font size=\"10\"/>\n");
            xml.append("                </textElement>\n");
            xml.append(String.format("                <textFieldExpression><![CDATA[$F{%s}]]></textFieldExpression>\n", column));
            xml.append("            </textField>\n");
        }

        return xml.toString();
    }
}

