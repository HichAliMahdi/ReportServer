package com.reportserver.controller;

import com.reportserver.model.ReportExecutionLog;
import com.reportserver.model.ReportTemplate;
import com.reportserver.model.SharedReport;
import com.reportserver.repository.ReportTemplateRepository;
import com.reportserver.repository.SharedReportRepository;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.ReportExecutionLogService;
import com.reportserver.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Controller
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private com.reportserver.service.JRDataSourceProviderService jrDataSourceProviderService;
    
    @Autowired
    private SharedReportRepository sharedReportRepository;
    
    @Autowired
    private com.reportserver.service.JrxmlValidator jrxmlValidator;

    @Autowired
    private ReportTemplateRepository reportTemplateRepository;

    @Autowired
    private ReportExecutionLogService reportExecutionLogService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;
    @Autowired
    private com.reportserver.service.ThumbnailGenerationService thumbnailGenerationService;

    @Autowired
    private com.reportserver.repository.ReportThumbnailRepository reportThumbnailRepository;


    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;
    
    private static final String GENERATED_REPORTS_DIR = "data/generated-reports/";
    
    @PostConstruct
    public void init() {
        // Create directories if they don't exist
        File uploadDir = new File(this.uploadDir);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
            logger.info("Created upload directory: " + uploadDir.getAbsolutePath());
        }
        
        File generatedDir = new File(GENERATED_REPORTS_DIR);
        if (!generatedDir.exists()) {
            generatedDir.mkdirs();
            logger.info("Created generated reports directory: " + generatedDir.getAbsolutePath());
        }
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<String> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description) {
        try {
            // Validate file
            if (file.isEmpty()) {
                logger.warn("Upload attempt with empty file");
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".jrxml")) {
                logger.warn("Upload attempt with invalid file type: " + filename);
                return ResponseEntity.badRequest().body("Only .jrxml files are allowed");
            }
            
            logger.info("Uploading file: " + filename + " (" + file.getSize() + " bytes)");
            
            // Validate JRXML content before saving
            String jrxmlContent = new String(file.getBytes());
            com.reportserver.service.JrxmlValidator.JrxmlValidationResult validation = 
                jrxmlValidator.validate(jrxmlContent);
            
            if (!validation.valid) {
                logger.warn("JRXML validation failed for file: {}. Issues: {}", filename, validation.getIssues());
                return ResponseEntity.badRequest().body(
                    "JRXML validation failed. Security issues detected: " + 
                    String.join(", ", validation.getIssues())
                );
            }
            
            // Create directory if it doesn't exist
            File uploadDir = new File(this.uploadDir);
            if (!uploadDir.exists()) {
                logger.info("Creating upload directory: " + uploadDir.getAbsolutePath());
                uploadDir.mkdirs();
            }

            // Save the file
            Path path = Paths.get(this.uploadDir + filename);
            Files.write(path, file.getBytes());

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String username = authentication != null ? authentication.getName() : "system";

                ReportTemplate template = reportTemplateRepository.findByReportFileName(filename)
                    .orElseGet(ReportTemplate::new);
                template.setReportFileName(filename);
                template.setDisplayName(filename.replace(".jrxml", ""));
                template.setCategory(safeNullable(category));
                template.setTags(safeNullable(tags));
                template.setDescription(safeNullable(description));
                template.setCreatedBy(template.getCreatedBy() == null ? username : template.getCreatedBy());
                reportTemplateRepository.save(template);
            
            logger.info("File uploaded successfully: " + filename);
            // Generate thumbnail for the uploaded report
            try {
                String jasperPath = this.uploadDir + filename.replace(".jrxml", ".jasper");
                String thumbnailPath = thumbnailGenerationService.generateThumbnail(
                    jasperPath,
                    filename,
                    new HashMap<>()
                );

                if (thumbnailPath != null) {
                    com.reportserver.model.ReportThumbnail thumbnail = new com.reportserver.model.ReportThumbnail(
                        template.getId(),
                        thumbnailPath
                    );
                    File thumbFile = new File("data/thumbnails" + thumbnailPath);
                    if (thumbFile.exists()) {
                        thumbnail.setFileSize(thumbFile.length());
                    }
                    reportThumbnailRepository.save(thumbnail);
                    logger.info("Generated thumbnail for report: {}", filename);
                }
            } catch (Exception e) {
                logger.warn("Failed to generate thumbnail for {}: {}", filename, e.getMessage());
                // Non-fatal error; report upload succeeded even if thumbnail failed
            }
            
            return ResponseEntity.ok("File uploaded successfully: " + filename);
        } catch (Exception e) {
            logger.error("Failed to upload report file", e);
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @RequestParam(value = "useDatabase", defaultValue = "false") boolean useDatabase,
            @RequestParam(value = "datasourceId", required = false) Long datasourceId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(required = false) Map<String, String> parameters) {
        
        Map<String, Object> response = new HashMap<>();
        Long logId = null;
        try {
            logger.info("Generating report: {} in format: {}, useDatabase: {}, datasourceId: {}", 
                       reportName, format, useDatabase, datasourceId);
            
            String jrxmlPath = this.uploadDir + reportName;
            
            // Check if file exists
            File reportFile = new File(jrxmlPath);
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                response.put("status", "error");
                response.put("message", "Report file not found: " + reportName);
                return ResponseEntity.badRequest().body(response);
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";

            // Validate and coerce user parameters to expected JRXML types.
            Map<String, Object> reportParams = validateAndConvertReportParameters(jrxmlPath, parameters);
            ReportExecutionLog log = reportExecutionLogService.startLog(
                    reportName,
                    format,
                    "MANUAL",
                    username,
                    datasourceId,
                    null,
                    reportParams
            );
            logId = log.getId();

            // Get database connection or datasource if requested
            Object dataSource = null;
            if (useDatabase) {
                if (datasourceId != null) {
                    logger.info("Getting datasource for ID: {}", datasourceId);
                    com.reportserver.model.DataSource ds = dataSourceService.getDataSourceById(datasourceId)
                            .orElseThrow(() -> new IllegalArgumentException("Datasource not found"));
                    dataSource = jrDataSourceProviderService.getDataSource(ds, reportParams);
                    if (dataSource == null) {
                        throw new IllegalArgumentException("Failed to create datasource");
                    }
                } else {
                    throw new IllegalArgumentException("Please select a datasource when using database connection");
                }
            }

            // Generate report
            logger.info("Compiling and filling report...");
            byte[] reportBytes;
            if (dataSource instanceof Connection) {
                reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, (Connection) dataSource);
            } else if (dataSource != null) {
                reportBytes = reportService.generateReportWithDataSource(jrxmlPath, reportParams, format, dataSource);
            } else {
                reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, null);
            }
            logger.info("Report generated successfully, size: {} bytes", reportBytes.length);

            // Save report to file system and database
            String extension = getFileExtension(format);
            String fileName = reportName.replace(".jrxml", "") + "_" + System.currentTimeMillis() + "." + extension;
            String generatedFilePath = GENERATED_REPORTS_DIR + fileName;
            
            // Create directory if it doesn't exist
            File generatedDir = new File(GENERATED_REPORTS_DIR);
            if (!generatedDir.exists()) {
                generatedDir.mkdirs();
            }
            
            // Write file to filesystem
            Files.write(Paths.get(generatedFilePath), reportBytes);
            logger.info("Report saved to: {}", generatedFilePath);
            
            // Save to database
            
            SharedReport sharedReport = new SharedReport();
            sharedReport.setReportFileName(fileName);
            sharedReport.setReportName(reportName.replace(".jrxml", ""));
            sharedReport.setReportFormat(format);
            sharedReport.setCategory(safeNullable(category));
            sharedReport.setTags(safeNullable(tags));
            sharedReport.setCreatedBy(username);
            sharedReport.setCreatedAt(LocalDateTime.now());
            sharedReport.setSharedWithReadOnly(false); // Not shared by default
            
            SharedReport savedReport = sharedReportRepository.save(sharedReport);
            logger.info("Report saved to database with ID: {}", savedReport.getId());
            
            response.put("status", "success");
            response.put("message", "Report generated successfully");
            response.put("reportId", savedReport.getId());
            response.put("fileName", fileName);
            if (logId != null) {
                reportExecutionLogService.markSuccess(logId, fileName);
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to generate report: " + reportName, e);
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
            if (logId != null) {
                reportExecutionLogService.markFailed(logId, e.getMessage());
            }
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Download report endpoint for READ_ONLY users (simplified, no parameters)
    @PostMapping("/download-report")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READ_ONLY')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format) {
        Long logId = null;
        try {
            logger.info("Downloading report: {} in format: {}", reportName, format);
            
            String jrxmlPath = this.uploadDir + reportName;
            
            // Check if file exists
            File reportFile = new File(jrxmlPath);
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                return ResponseEntity.badRequest()
                    .body(("Report file not found: " + reportName).getBytes());
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";
            ReportExecutionLog log = reportExecutionLogService.startLog(
                    reportName,
                    format,
                    "MANUAL",
                    username,
                    null,
                    null,
                    new HashMap<>()
            );
            logId = log.getId();
            
            // Generate report without parameters
            logger.info("Compiling and filling report...");
            byte[] reportBytes = reportService.generateReport(jrxmlPath, new HashMap<>(), format, null);
            logger.info("Report downloaded successfully, size: {} bytes", reportBytes.length);

            // Set content type and extension based on format
            MediaType contentType = getContentType(format);
            String extension = getFileExtension(format);

            if (logId != null) {
                reportExecutionLogService.markSuccess(logId, reportName.replace(".jrxml", "." + extension));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDispositionFormData("attachment", 
                reportName.replace(".jrxml", "." + extension));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportBytes);

        } catch (Exception e) {
            logger.error("Failed to download report: " + reportName, e);
            if (logId != null) {
                reportExecutionLogService.markFailed(logId, e.getMessage());
            }
            return ResponseEntity.badRequest().body(("Error: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {
        File dir = new File(this.uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
        int resolvedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(resolvedPage, Math.max(resolvedSize, 1));

        String[] fileNames = dir.list((d, name) -> name.endsWith(".jrxml"));
        if (fileNames == null) {
            fileNames = new String[0];
        }

        Map<String, ReportTemplate> metadataByFile = reportTemplateRepository.findAll().stream()
                .collect(Collectors.toMap(ReportTemplate::getReportFileName, template -> template, (a, b) -> a));

        List<Map<String, Object>> all = Stream.of(fileNames)
                .sorted(String::compareToIgnoreCase)
                .map(file -> {
                    ReportTemplate meta = metadataByFile.get(file);
                    Map<String, Object> item = new HashMap<>();
                    item.put("reportFileName", file);
                    item.put("reportName", file.replace(".jrxml", ""));
                    item.put("category", meta != null ? meta.getCategory() : null);
                    item.put("tags", meta != null ? meta.getTags() : null);
                    item.put("description", meta != null ? meta.getDescription() : null);
                    return item;
                })
                .filter(item -> {
                    if (category == null || category.isBlank()) {
                        return true;
                    }
                    String itemCategory = (String) item.get("category");
                    return itemCategory != null && itemCategory.toLowerCase().contains(category.toLowerCase());
                })
                .filter(item -> {
                    if (tag == null || tag.isBlank()) {
                        return true;
                    }
                    String itemTags = (String) item.get("tags");
                    return itemTags != null && itemTags.toLowerCase().contains(tag.toLowerCase());
                })
                .collect(Collectors.toList());

        int start = Math.min((int) pageable.getOffset(), all.size());
        int end = Math.min(start + pageable.getPageSize(), all.size());
        Page<Map<String, Object>> resultPage = new PageImpl<>(all.subList(start, end), pageable, all.size());

        Map<String, Object> response = new HashMap<>();
        response.put("content", resultPage.getContent());
        response.put("page", resultPage.getNumber());
        response.put("size", resultPage.getSize());
        response.put("totalElements", resultPage.getTotalElements());
        response.put("totalPages", resultPage.getTotalPages());
        response.put("first", resultPage.isFirst());
        response.put("last", resultPage.isLast());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/reports/{reportName}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<String> deleteReport(@PathVariable String reportName) {
        try {
            // Validate file name (security check to prevent path traversal)
            if (reportName.contains("..") || reportName.contains("/") || reportName.contains("\\")) {
                logger.warn("Invalid report name in delete request: " + reportName);
                return ResponseEntity.badRequest().body("Invalid report name");
            }
            
            if (!reportName.toLowerCase().endsWith(".jrxml")) {
                logger.warn("Delete attempt with invalid file type: " + reportName);
                return ResponseEntity.badRequest().body("Only .jrxml files can be deleted");
            }
            
            Path reportPath = Paths.get(this.uploadDir + reportName);
            File reportFile = reportPath.toFile();
            
            if (!reportFile.exists()) {
                logger.warn("Delete attempt for non-existent file: " + reportName);
                return ResponseEntity.badRequest().body("Report file not found: " + reportName);
            }
            
            if (reportFile.delete()) {
                reportTemplateRepository.findByReportFileName(reportName)
                        .ifPresent(template -> reportTemplateRepository.deleteById(template.getId()));
                logger.info("Report deleted successfully: " + reportName);
                return ResponseEntity.ok("Report deleted successfully: " + reportName);
            } else {
                logger.error("Failed to delete report file: " + reportName);
                return ResponseEntity.status(500).body("Failed to delete report file");
            }
        } catch (Exception e) {
            logger.error("Error deleting report: " + reportName, e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private Map<String, Object> validateAndConvertReportParameters(String jrxmlPath, Map<String, String> requestParameters) {
        Map<String, Object> converted = new HashMap<>();
        if (requestParameters == null || requestParameters.isEmpty()) {
            return converted;
        }

        Map<String, String> expectedTypes = readExpectedParameterTypes(jrxmlPath);
        for (Map.Entry<String, String> entry : requestParameters.entrySet()) {
            String key = entry.getKey();
            if (isReservedGenerateParameterName(key)) {
                continue;
            }

            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            String expectedType = expectedTypes.get(key);
            if (expectedType == null) {
                converted.put(key, rawValue);
                continue;
            }

            converted.put(key, convertParameterValue(key, rawValue, expectedType));
        }
        return converted;
    }

    private boolean isReservedGenerateParameterName(String key) {
        return "reportName".equals(key)
                || "format".equals(key)
                || "useDatabase".equals(key)
                || "datasourceId".equals(key)
                || "category".equals(key)
                || "tags".equals(key)
                || "_csrf".equals(key);
    }

    private Map<String, String> readExpectedParameterTypes(String jrxmlPath) {
        Map<String, String> expectedTypes = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(jrxmlPath));
            NodeList parameterNodes = document.getElementsByTagName("parameter");

            for (int i = 0; i < parameterNodes.getLength(); i++) {
                Element paramElement = (Element) parameterNodes.item(i);
                String name = paramElement.getAttribute("name");
                String className = paramElement.getAttribute("class");
                if (!name.startsWith("REPORT_") && !name.equals("JASPER_REPORT")) {
                    expectedTypes.put(name, className);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse JRXML parameter definitions for validation: {}", e.getMessage());
        }
        return expectedTypes;
    }

    private Object convertParameterValue(String parameterName, String rawValue, String javaType) {
        try {
            if (javaType == null || javaType.isBlank() || javaType.contains("String")) {
                return rawValue;
            }
            if (javaType.contains("Boolean")) {
                if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                    throw new IllegalArgumentException("must be true or false");
                }
                return Boolean.parseBoolean(rawValue);
            }
            if (javaType.contains("Integer")) {
                return Integer.parseInt(rawValue);
            }
            if (javaType.contains("Long")) {
                return Long.parseLong(rawValue);
            }
            if (javaType.contains("Double")) {
                return Double.parseDouble(rawValue);
            }
            if (javaType.contains("Float")) {
                return Float.parseFloat(rawValue);
            }
            if (javaType.contains("BigDecimal")) {
                return new BigDecimal(rawValue);
            }
            if (javaType.contains("BigInteger")) {
                return new BigInteger(rawValue);
            }
            if (javaType.contains("Timestamp")) {
                return Timestamp.from(Instant.parse(rawValue + "T00:00:00Z"));
            }
            if (javaType.contains("Date")) {
                return Date.valueOf(rawValue);
            }
            return rawValue;
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Parameter '" + parameterName + "' expects " + javaType + " but got value '" + rawValue + "'");
        }
    }

    private String safeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MediaType getContentType(String format) {
        switch (format.toLowerCase()) {
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "html":
                return MediaType.TEXT_HTML;
            case "xlsx":
            case "xls":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            case "docx":
                return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "rtf":
                return MediaType.parseMediaType("application/rtf");
            case "odt":
                return MediaType.parseMediaType("application/vnd.oasis.opendocument.text");
            case "csv":
                return MediaType.parseMediaType("text/csv");
            case "xml":
                return MediaType.APPLICATION_XML;
            case "txt":
            case "text":
                return MediaType.TEXT_PLAIN;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String getFileExtension(String format) {
        switch (format.toLowerCase()) {
            case "pdf":
                return "pdf";
            case "html":
                return "html";
            case "xlsx":
                return "xlsx";
            case "xls":
                return "xls";
            case "docx":
                return "docx";
            case "rtf":
                return "rtf";
            case "odt":
                return "odt";
            case "csv":
                return "csv";
            case "xml":
                return "xml";
            case "txt":
            case "text":
                return "txt";
            default:
                return "pdf";
        }
    }
    
    // API: Get all generated reports (with share status)
    @GetMapping("/api/generated-reports")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGeneratedReports(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean sharedOnly) {
        try {
            boolean isReadOnly = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_READ_ONLY".equals(a.getAuthority()));
            boolean effectiveSharedOnly = sharedOnly || isReadOnly;

            int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(resolvedSize, 1));

            Page<SharedReport> reports;
            if (category != null && !category.isBlank() && tag != null && !tag.isBlank()) {
                reports = sharedReportRepository.findByCategoryContainingIgnoreCaseAndTagsContainingIgnoreCaseOrderByCreatedAtDesc(category, tag, pageable);
            } else if (category != null && !category.isBlank()) {
                reports = sharedReportRepository.findByCategoryContainingIgnoreCaseOrderByCreatedAtDesc(category, pageable);
            } else if (tag != null && !tag.isBlank()) {
                reports = sharedReportRepository.findByTagsContainingIgnoreCaseOrderByCreatedAtDesc(tag, pageable);
            } else if (effectiveSharedOnly) {
                reports = sharedReportRepository.findBySharedWithReadOnlyTrueOrderByCreatedAtDesc(pageable);
            } else {
                reports = sharedReportRepository.findAllByOrderByCreatedAtDesc(pageable);
            }

            List<Map<String, Object>> content = reports.getContent().stream().map(report -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", report.getId());
                item.put("reportFileName", report.getReportFileName());
                item.put("reportName", report.getReportName());
                item.put("reportFormat", report.getReportFormat());
                item.put("category", report.getCategory());
                item.put("tags", report.getTags());
                item.put("sharedWithReadOnly", report.isSharedWithReadOnly());
                item.put("createdAt", report.getCreatedAt());
                item.put("createdBy", report.getCreatedBy());
                item.put("sharedAt", report.getSharedAt());
                item.put("sharedBy", report.getSharedBy());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("page", reports.getNumber());
            response.put("size", reports.getSize());
            response.put("totalElements", reports.getTotalElements());
            response.put("totalPages", reports.getTotalPages());
            response.put("first", reports.isFirst());
            response.put("last", reports.isLast());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting generated reports", e);
            return ResponseEntity.status(500).body(null);
        }
    }
    
    // API: Toggle share status of a generated report
    @PostMapping("/api/generated-reports/{reportId}/toggle-share")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleShareReport(@PathVariable Long reportId, @RequestBody Map<String, Boolean> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<SharedReport> optionalReport = sharedReportRepository.findById(reportId);
            if (!optionalReport.isPresent()) {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.badRequest().body(response);
            }
            
            SharedReport report = optionalReport.get();
            Boolean shouldShare = request.get("share");
            
            if (shouldShare != null) {
                report.setSharedWithReadOnly(shouldShare);
                if (shouldShare) {
                    report.setSharedAt(LocalDateTime.now());
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    report.setSharedBy(auth.getName());
                }
                sharedReportRepository.save(report);
                
                String message = shouldShare ? "Report shared with READ_ONLY users" : "Report unshared from READ_ONLY users";
                response.put("status", "success");
                response.put("message", message);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Share parameter is required");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error toggling share status", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // API: Delete a generated report
    @DeleteMapping("/api/generated-reports/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteGeneratedReport(@PathVariable Long reportId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<SharedReport> optionalReport = sharedReportRepository.findById(reportId);
            if (!optionalReport.isPresent()) {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.badRequest().body(response);
            }
            
            SharedReport report = optionalReport.get();
            File reportFile = new File(GENERATED_REPORTS_DIR + report.getReportFileName());
            
            if (reportFile.exists()) {
                reportFile.delete();
                logger.info("Deleted report file: {}", report.getReportFileName());
            }
            
            sharedReportRepository.deleteById(reportId);
            
            response.put("status", "success");
            response.put("message", "Report deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting generated report", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // API: Download a generated report
    @GetMapping("/api/download-generated-report/{fileName}")
    public ResponseEntity<byte[]> downloadGeneratedReport(@PathVariable String fileName) {
        try {
            // Validate file name (security check to prevent path traversal)
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                logger.warn("Invalid file name in download request: " + fileName);
                return ResponseEntity.badRequest().build();
            }
            
            Path filePath = Paths.get(GENERATED_REPORTS_DIR + fileName);
            File file = filePath.toFile();
            
            if (!file.exists()) {
                logger.warn("Download attempt for non-existent file: " + fileName);
                return ResponseEntity.notFound().build();
            }
            
            byte[] fileContent = Files.readAllBytes(filePath);
            
            // Determine content type from file extension
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            MediaType contentType = getContentType(extension);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(fileContent.length);
            
            logger.info("Downloaded generated report: {}", fileName);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
        } catch (Exception e) {
            logger.error("Error downloading generated report: " + fileName, e);
            return ResponseEntity.status(500).build();
        }
    }
}
