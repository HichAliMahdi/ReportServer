package com.reportserver.controller;

import com.reportserver.service.DataSourceService;
import com.reportserver.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private com.reportserver.service.JRDataSourceProviderService jrDataSourceProviderService;

    private static final String UPLOAD_DIR = "data/reports/";

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<String> uploadReport(@RequestParam("file") MultipartFile file) {
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
            
            // Create directory if it doesn't exist
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                logger.info("Creating upload directory: " + uploadDir.getAbsolutePath());
                uploadDir.mkdirs();
            }

            // Save the file
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.write(path, file.getBytes());
            
            logger.info("File uploaded successfully: " + filename);
            return ResponseEntity.ok("File uploaded successfully: " + filename);
        } catch (Exception e) {
            logger.error("Failed to upload report file", e);
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<byte[]> generateReport(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @RequestParam(value = "useDatabase", defaultValue = "false") boolean useDatabase,
            @RequestParam(value = "datasourceId", required = false) Long datasourceId,
            @RequestParam(required = false) Map<String, String> parameters) {
        
        Connection connection = null;
        try {
            logger.info("Generating report: {} in format: {}, useDatabase: {}, datasourceId: {}", 
                       reportName, format, useDatabase, datasourceId);
            
            String jrxmlPath = UPLOAD_DIR + reportName;
            
            // Check if file exists
            File reportFile = new File(jrxmlPath);
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                return ResponseEntity.badRequest()
                    .body(("Report file not found: " + reportName).getBytes());
            }
            
            // Add parameters to report
            Map<String, Object> reportParams = new HashMap<>();
            if (parameters != null) {
                reportParams.putAll(parameters);
            }

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

            // Set content type and extension based on format
            MediaType contentType = getContentType(format);
            String extension = getFileExtension(format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDispositionFormData("attachment", 
                reportName.replace(".jrxml", "." + extension));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportBytes);

        } catch (Exception e) {
            logger.error("Failed to generate report: " + reportName, e);
            return ResponseEntity.badRequest().body(("Error: " + e.getMessage()).getBytes());
        } finally {
            // Always close the connection if it was opened
            if (connection instanceof Connection) {
                try {
                    ((Connection) connection).close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    // Download report endpoint for READ_ONLY users (simplified, no parameters)
    @PostMapping("/download-report")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READ_ONLY')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format) {
        
        try {
            logger.info("Downloading report: {} in format: {}", reportName, format);
            
            String jrxmlPath = UPLOAD_DIR + reportName;
            
            // Check if file exists
            File reportFile = new File(jrxmlPath);
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                return ResponseEntity.badRequest()
                    .body(("Report file not found: " + reportName).getBytes());
            }
            
            // Generate report without parameters
            logger.info("Compiling and filling report...");
            byte[] reportBytes = reportService.generateReport(jrxmlPath, new HashMap<>(), format, null);
            logger.info("Report downloaded successfully, size: {} bytes", reportBytes.length);

            // Set content type and extension based on format
            MediaType contentType = getContentType(format);
            String extension = getFileExtension(format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDispositionFormData("attachment", 
                reportName.replace(".jrxml", "." + extension));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportBytes);

        } catch (Exception e) {
            logger.error("Failed to download report: " + reportName, e);
            return ResponseEntity.badRequest().body(("Error: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports")
    @ResponseBody
    public ResponseEntity<String[]> listReports() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String[] files = dir.list((d, name) -> name.endsWith(".jrxml"));
        return ResponseEntity.ok(files != null ? files : new String[0]);
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
            
            Path reportPath = Paths.get(UPLOAD_DIR + reportName);
            File reportFile = reportPath.toFile();
            
            if (!reportFile.exists()) {
                logger.warn("Delete attempt for non-existent file: " + reportName);
                return ResponseEntity.badRequest().body("Report file not found: " + reportName);
            }
            
            if (reportFile.delete()) {
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
}
