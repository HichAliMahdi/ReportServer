package com.reportserver.controller;

import com.reportserver.dto.ReportDownloadRequestDTO;
import com.reportserver.dto.ReportGenerateRequestDTO;
import com.reportserver.model.ReportExecutionLog;
import com.reportserver.model.ReportShareRecipient;
import com.reportserver.model.ReportTemplate;
import com.reportserver.model.SharedReport;
import com.reportserver.model.User;
import com.reportserver.repository.ReportTemplateRepository;
import com.reportserver.repository.ReportShareRecipientRepository;
import com.reportserver.repository.SharedReportRepository;
import com.reportserver.repository.UserRepository;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.JrxmlParameterService;
import com.reportserver.service.PdfUtilityService;
import com.reportserver.service.ReportExecutionLogService;
import com.reportserver.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import com.reportserver.model.ReportShareToken;
import com.reportserver.repository.ReportShareTokenRepository;
import org.springframework.http.HttpStatus;
import jakarta.annotation.PostConstruct;

@Controller
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private ReportService reportService;
    
    private DataSourceService dataSourceService;

    private PdfUtilityService pdfUtilityService;

    private com.reportserver.service.JRDataSourceProviderService jrDataSourceProviderService;

    private SharedReportRepository sharedReportRepository;

    private com.reportserver.service.JrxmlValidator jrxmlValidator;

    private ReportTemplateRepository reportTemplateRepository;

    private ReportExecutionLogService reportExecutionLogService;

    private JrxmlParameterService jrxmlParameterService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;
    private com.reportserver.service.ThumbnailGenerationService thumbnailGenerationService;

    private com.reportserver.repository.ReportThumbnailRepository reportThumbnailRepository;

    private ReportShareTokenRepository shareTokenRepository;

    private ReportShareRecipientRepository reportShareRecipientRepository;

    private UserRepository userRepository;


    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;
    
    private static final String GENERATED_REPORTS_DIR = "data/generated-reports/";

    public ReportController(ReportService reportService,
                            DataSourceService dataSourceService,
                            PdfUtilityService pdfUtilityService,
                            com.reportserver.service.JRDataSourceProviderService jrDataSourceProviderService,
                            SharedReportRepository sharedReportRepository,
                            com.reportserver.service.JrxmlValidator jrxmlValidator,
                            ReportTemplateRepository reportTemplateRepository,
                            ReportExecutionLogService reportExecutionLogService,
                            JrxmlParameterService jrxmlParameterService,
                            com.reportserver.service.ThumbnailGenerationService thumbnailGenerationService,
                            com.reportserver.repository.ReportThumbnailRepository reportThumbnailRepository,
                            ReportShareTokenRepository shareTokenRepository,
                            ReportShareRecipientRepository reportShareRecipientRepository,
                            UserRepository userRepository) {
        this.reportService = reportService;
        this.dataSourceService = dataSourceService;
        this.pdfUtilityService = pdfUtilityService;
        this.jrDataSourceProviderService = jrDataSourceProviderService;
        this.sharedReportRepository = sharedReportRepository;
        this.jrxmlValidator = jrxmlValidator;
        this.reportTemplateRepository = reportTemplateRepository;
        this.reportExecutionLogService = reportExecutionLogService;
        this.jrxmlParameterService = jrxmlParameterService;
        this.thumbnailGenerationService = thumbnailGenerationService;
        this.reportThumbnailRepository = reportThumbnailRepository;
        this.shareTokenRepository = shareTokenRepository;
        this.reportShareRecipientRepository = reportShareRecipientRepository;
        this.userRepository = userRepository;
    }
    
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
            Path path = resolveReportPath(filename);
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
                String jasperPath = resolveReportPath(filename.replace(".jrxml", ".jasper")).toString();
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
            @Valid @ModelAttribute ReportGenerateRequestDTO request,
            BindingResult bindingResult,
            @RequestParam(required = false) Map<String, String> parameters) {

        if (bindingResult.hasErrors()) {
            Map<String, Object> validationError = new HashMap<>();
            validationError.put("status", "error");
            validationError.put("message", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return ResponseEntity.badRequest().body(validationError);
        }

        String reportName = request.getReportName();
        String format = request.getFormat();
        boolean useDatabase = request.isUseDatabase();
        Long datasourceId = request.getDatasourceId();
        String category = request.getCategory();
        String tags = request.getTags();
        
        Map<String, Object> response = new HashMap<>();
        Long logId = null;
        try {
            logger.info("Generating report: {} in format: {}, useDatabase: {}, datasourceId: {}", 
                       reportName, format, useDatabase, datasourceId);
            
            Path jrxmlPath = resolveReportPath(reportName);
            
            // Check if file exists
            File reportFile = jrxmlPath.toFile();
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                response.put("status", "error");
                response.put("message", "Report file not found: " + reportName);
                return ResponseEntity.badRequest().body(response);
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";

            // Validate and coerce user parameters to expected JRXML types.
            Map<String, Object> reportParams = jrxmlParameterService.validateAndConvertReportParameters(jrxmlPath.toString(), parameters);
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
                reportBytes = reportService.generateReport(jrxmlPath.toString(), reportParams, format, (Connection) dataSource);
            } else if (dataSource != null) {
                reportBytes = reportService.generateReportWithDataSource(jrxmlPath.toString(), reportParams, format, dataSource);
            } else {
                reportBytes = reportService.generateReport(jrxmlPath.toString(), reportParams, format, null);
            }
            logger.info("Report generated successfully, size: {} bytes", reportBytes.length);

            // Save report to file system and database
            String extension = getFileExtension(format);
            String fileName = reportName.replace(".jrxml", "") + "_" + System.currentTimeMillis() + "." + extension;
            Path generatedFilePath = resolveGeneratedReportPath(fileName);
            
            // Create directory if it doesn't exist
            File generatedDir = new File(GENERATED_REPORTS_DIR);
            if (!generatedDir.exists()) {
                generatedDir.mkdirs();
            }
            
            // Write file to filesystem
            Files.write(generatedFilePath, reportBytes);
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
            @Valid @ModelAttribute ReportDownloadRequestDTO request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(message.getBytes());
        }

        String reportName = request.getReportName();
        String format = request.getFormat();

        Long logId = null;
        try {
            logger.info("Downloading report: {} in format: {}", reportName, format);
            
            Path jrxmlPath = resolveReportPath(reportName);
            
            // Check if file exists
            File reportFile = jrxmlPath.toFile();
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
            byte[] reportBytes = reportService.generateReport(jrxmlPath.toString(), new HashMap<>(), format, null);
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
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search) {
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
                .filter(item -> {
                    if (search == null || search.isBlank()) {
                        return true;
                    }
                    String lower = search.toLowerCase();
                    String itemFileName = (String) item.get("reportFileName");
                    String itemName = (String) item.get("reportName");
                    return (itemFileName != null && itemFileName.toLowerCase().contains(lower))
                            || (itemName != null && itemName.toLowerCase().contains(lower));
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
            
            Path reportPath = resolveReportPath(reportName);
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

    private Path resolveReportPath(String fileName) {
        return resolveWithinBaseDirectory(this.uploadDir, fileName);
    }

    private Path resolveGeneratedReportPath(String fileName) {
        return resolveWithinBaseDirectory(GENERATED_REPORTS_DIR, fileName);
    }

    private Path resolveWithinBaseDirectory(String baseDirectory, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        Path basePath = Paths.get(baseDirectory).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(fileName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Path traversal detected");
        }
        return resolved;
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
            Long currentUserId = null;
            if (isReadOnly && authentication != null) {
                currentUserId = userRepository.findByUsername(authentication.getName())
                    .map(User::getId)
                    .orElse(null);
            }

            if (isReadOnly && currentUserId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("content", List.of());
                response.put("page", 0);
                response.put("size", 0);
                response.put("totalElements", 0);
                response.put("totalPages", 0);
                response.put("first", true);
                response.put("last", true);
                return ResponseEntity.ok(response);
            }

            int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(resolvedSize, 1));

            Page<SharedReport> reports;
            if (isReadOnly && currentUserId != null) {
                if (category != null && !category.isBlank() && tag != null && !tag.isBlank()) {
                    reports = sharedReportRepository.findAccessibleForReadOnlyByCategoryAndTag(currentUserId, category, tag, pageable);
                } else if (category != null && !category.isBlank()) {
                    reports = sharedReportRepository.findAccessibleForReadOnlyByCategory(currentUserId, category, pageable);
                } else if (tag != null && !tag.isBlank()) {
                    reports = sharedReportRepository.findAccessibleForReadOnlyByTag(currentUserId, tag, pageable);
                } else {
                    reports = sharedReportRepository.findAccessibleForReadOnly(currentUserId, pageable);
                }
            } else {
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
                long specificShareCount = reportShareRecipientRepository.countByReport_Id(report.getId());
                item.put("sharedScope", report.isSharedWithReadOnly() ? "ALL" : (specificShareCount > 0 ? "SPECIFIC" : "NONE"));
                item.put("sharedUserCount", specificShareCount);
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
                if (!shouldShare) {
                    reportShareRecipientRepository.deleteByReport_Id(reportId);
                }
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

    @GetMapping("/api/generated-reports/{reportId}/share-config")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getShareConfig(@PathVariable Long reportId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<SharedReport> optionalReport = sharedReportRepository.findById(reportId);
            if (optionalReport.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.badRequest().body(response);
            }

            SharedReport report = optionalReport.get();
            List<ReportShareRecipient> recipients = reportShareRecipientRepository.findByReport_Id(reportId);
            List<Long> selectedUserIds = recipients.stream().map(r -> r.getUser().getId()).distinct().collect(Collectors.toList());

            String scope = report.isSharedWithReadOnly() ? "ALL" : (selectedUserIds.isEmpty() ? "NONE" : "SPECIFIC");

            List<Map<String, Object>> availableUsers = userRepository.findByRoleAndEnabledTrueOrderByUsernameAsc("READ_ONLY")
                .stream()
                .map(user -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", user.getId());
                    item.put("username", user.getUsername());
                    item.put("email", user.getEmail());
                    return item;
                })
                .collect(Collectors.toList());

            response.put("status", "success");
            response.put("scope", scope);
            response.put("selectedUserIds", selectedUserIds);
            response.put("availableUsers", availableUsers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error loading share config", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/generated-reports/{reportId}/share-config")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveShareConfig(@PathVariable Long reportId, @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<SharedReport> optionalReport = sharedReportRepository.findById(reportId);
            if (optionalReport.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.badRequest().body(response);
            }

            SharedReport report = optionalReport.get();
            String scope = String.valueOf(request.getOrDefault("scope", "NONE")).trim().toUpperCase();

            Set<Long> requestedUserIds = new LinkedHashSet<>();
            Object usersValue = request.get("userIds");
            if (usersValue instanceof List<?> rawIds) {
                rawIds.stream()
                    .filter(item -> item instanceof Number || item instanceof String)
                    .forEach(item -> {
                        try {
                            requestedUserIds.add(Long.parseLong(String.valueOf(item)));
                        } catch (NumberFormatException ignore) {
                            // Ignore invalid values.
                        }
                    });
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actor = auth != null ? auth.getName() : "system";

            if (!"ALL".equals(scope) && !"SPECIFIC".equals(scope) && !"NONE".equals(scope)) {
                response.put("status", "error");
                response.put("message", "Invalid share scope");
                return ResponseEntity.badRequest().body(response);
            }

            reportShareRecipientRepository.deleteByReport_Id(reportId);

            if ("ALL".equals(scope)) {
                report.setSharedWithReadOnly(true);
                report.setSharedAt(LocalDateTime.now());
                report.setSharedBy(actor);
                sharedReportRepository.save(report);
                response.put("status", "success");
                response.put("message", "Report shared with all READ_ONLY users");
                return ResponseEntity.ok(response);
            }

            if ("SPECIFIC".equals(scope)) {
                if (requestedUserIds.isEmpty()) {
                    response.put("status", "error");
                    response.put("message", "Select at least one READ_ONLY user");
                    return ResponseEntity.badRequest().body(response);
                }

                List<User> selectedUsers = userRepository.findAllById(requestedUserIds).stream()
                    .filter(User::isEnabled)
                    .filter(user -> "READ_ONLY".equalsIgnoreCase(user.getRole()))
                    .collect(Collectors.toList());

                if (selectedUsers.isEmpty()) {
                    response.put("status", "error");
                    response.put("message", "No valid READ_ONLY users selected");
                    return ResponseEntity.badRequest().body(response);
                }

                report.setSharedWithReadOnly(false);
                report.setSharedAt(LocalDateTime.now());
                report.setSharedBy(actor);
                sharedReportRepository.save(report);

                List<ReportShareRecipient> recipients = selectedUsers.stream().map(user -> {
                    ReportShareRecipient recipient = new ReportShareRecipient();
                    recipient.setReport(report);
                    recipient.setUser(user);
                    recipient.setSharedBy(actor);
                    recipient.setSharedAt(LocalDateTime.now());
                    return recipient;
                }).collect(Collectors.toList());

                reportShareRecipientRepository.saveAll(recipients);
                response.put("status", "success");
                response.put("message", "Report shared with selected READ_ONLY users");
                return ResponseEntity.ok(response);
            }

            report.setSharedWithReadOnly(false);
            sharedReportRepository.save(report);
            response.put("status", "success");
            response.put("message", "Report is no longer shared with READ_ONLY users");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error saving share config", e);
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
            File reportFile = resolveGeneratedReportPath(report.getReportFileName()).toFile();
            
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
    public ResponseEntity<byte[]> downloadGeneratedReport(@PathVariable String fileName) {        try {
            Path filePath = resolveGeneratedReportPath(fileName);
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

    // API: Preview a generated report inline in the browser
    @GetMapping("/api/preview-generated-report/{fileName}")
    public ResponseEntity<byte[]> previewGeneratedReport(@PathVariable String fileName) {
        try {
            Path filePath = resolveGeneratedReportPath(fileName);
            File file = filePath.toFile();
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            MediaType contentType = getContentType(extension);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            headers.setContentLength(fileContent.length);

            return ResponseEntity.ok().headers(headers).body(fileContent);
        } catch (Exception e) {
            logger.error("Error previewing generated report: {}", fileName, e);
            return ResponseEntity.status(500).build();
        }
    }

    // API: Get a generated PDF page count for viewer navigation controls
    @GetMapping("/api/generated-reports/{fileName}/pdf-page-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGeneratedReportPdfPageCount(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!fileName.toLowerCase().endsWith(".pdf")) {
                response.put("status", "error");
                response.put("message", "Page count is available only for PDF files");
                return ResponseEntity.badRequest().body(response);
            }

            Path filePath = resolveGeneratedReportPath(fileName);
            File file = filePath.toFile();
            if (!file.exists()) {
                response.put("status", "error");
                response.put("message", "Report file not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            try {
                int pageCount = pdfUtilityService.getPdfPageCount(file);
                response.put("status", "success");
                response.put("fileName", fileName);
                response.put("pageCount", pageCount);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error calculating PDF page count for {}", fileName, e);
                response.put("status", "error");
                response.put("message", "Failed to read PDF page count");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            logger.error("Error processing PDF page count request", e);
            response.put("status", "error");
            response.put("message", "Failed to process request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // API: Create a temporary share link for a generated report
    @PostMapping("/api/generated-reports/{reportId}/create-share-link")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createShareLink(
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<SharedReport> optionalReport = sharedReportRepository.findById(reportId);
            if (!optionalReport.isPresent()) {
                response.put("status", "error");
                response.put("message", "Report not found");
                return ResponseEntity.badRequest().body(response);
            }

            SharedReport report = optionalReport.get();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";

            int expiryHours = 24;
            Object expiryObj = request.get("expiryHours");
            if (expiryObj instanceof Number) {
                expiryHours = Math.max(1, Math.min(((Number) expiryObj).intValue(), 720));
            }

            String token = UUID.randomUUID().toString().replace("-", "");
            ReportShareToken shareToken = new ReportShareToken();
            shareToken.setToken(token);
            shareToken.setReportFileName(report.getReportFileName());
            shareToken.setReportName(report.getReportName());
            shareToken.setCreatedBy(username);
            shareToken.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
            shareTokenRepository.save(shareToken);

            response.put("status", "success");
            response.put("token", token);
            response.put("expiresAt", shareToken.getExpiresAt().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating share link for report {}", reportId, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Public: Download a report via a temporary share token (no authentication needed)
    @GetMapping("/s/{token}")
    public ResponseEntity<byte[]> downloadViaShareToken(@PathVariable String token) {
        try {
            if (token == null || token.length() > 64 || !token.matches("[a-f0-9]+")) {
                return ResponseEntity.badRequest().build();
            }

            Optional<ReportShareToken> optToken = shareTokenRepository.findByToken(token);
            if (!optToken.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            ReportShareToken shareToken = optToken.get();
            if (shareToken.isRevoked() || shareToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            Path filePath = resolveGeneratedReportPath(shareToken.getReportFileName());
            File file = filePath.toFile();
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            String ext = shareToken.getReportFileName()
                    .substring(shareToken.getReportFileName().lastIndexOf('.') + 1).toLowerCase();
            MediaType contentType = getContentType(ext);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + shareToken.getReportFileName() + "\"");
            headers.setContentLength(fileContent.length);

            logger.info("Report downloaded via share token: {}", shareToken.getReportFileName());
            return ResponseEntity.ok().headers(headers).body(fileContent);
        } catch (Exception e) {
            logger.error("Error downloading via share token: {}", token, e);
            return ResponseEntity.status(500).build();
        }
    }

    // API: Revoke a share token
    @DeleteMapping("/api/share-tokens/{token}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> revokeShareToken(@PathVariable String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<ReportShareToken> optToken = shareTokenRepository.findByToken(token);
            if (!optToken.isPresent()) {
                response.put("status", "error");
                response.put("message", "Token not found");
                return ResponseEntity.badRequest().body(response);
            }
            ReportShareToken shareToken = optToken.get();
            shareToken.setRevoked(true);
            shareTokenRepository.save(shareToken);
            response.put("status", "success");
            response.put("message", "Share link revoked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error revoking share token: {}", token, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // API: List active share links for a generated report
    @GetMapping("/api/generated-reports/{reportId}/share-links")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getShareLinks(@PathVariable Long reportId) {
        try {
            Optional<SharedReport> optReport = sharedReportRepository.findById(reportId);
            if (!optReport.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            SharedReport report = optReport.get();
            List<ReportShareToken> tokens = shareTokenRepository
                    .findByReportFileNameAndRevokedFalse(report.getReportFileName());

            List<Map<String, Object>> result = tokens.stream()
                    .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                    .map(t -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("token", t.getToken());
                        item.put("createdBy", t.getCreatedBy());
                        item.put("createdAt", t.getCreatedAt().toString());
                        item.put("expiresAt", t.getExpiresAt().toString());
                        return item;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting share links for report {}", reportId, e);
            return ResponseEntity.status(500).build();
        }
    }
}
