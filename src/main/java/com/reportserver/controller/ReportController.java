package com.reportserver.controller;

import com.reportserver.service.DataSourceService;
import com.reportserver.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSourceService dataSourceService;

    private static final String UPLOAD_DIR = "data/reports/";

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/upload")
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
            
            // Convert String parameters to proper types if needed
            Map<String, Object> reportParams = new HashMap<>();
            if (parameters != null) {
                reportParams.putAll(parameters);
            }

            // Get database connection if requested
            if (useDatabase) {
                if (datasourceId != null) {
                    logger.info("Getting database connection for datasource ID: {}", datasourceId);
                    // Use the selected datasource
                    connection = dataSourceService.getConnection(datasourceId);
                    if (connection == null) {
                        throw new IllegalArgumentException("Failed to connect to datasource");
                    }
                } else {
                    throw new IllegalArgumentException("Please select a datasource when using database connection");
                }
            }

            // Generate report
            logger.info("Compiling and filling report...");
            byte[] reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, connection);
            logger.info("Report generated successfully, size: {} bytes", reportBytes.length);

            // Set content type based on format
            MediaType contentType = format.equalsIgnoreCase("xlsx") ? 
                MediaType.APPLICATION_OCTET_STREAM : MediaType.APPLICATION_PDF;

            String extension = format.equalsIgnoreCase("xlsx") ? "xlsx" : "pdf";

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
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
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
}
