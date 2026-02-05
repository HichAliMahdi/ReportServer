package com.reportserver.controller;

import com.reportserver.service.DatabaseConnectionService;
import com.reportserver.service.ReportService;
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

    @Autowired
    private ReportService reportService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    private static final String UPLOAD_DIR = "data/reports/";

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<String> uploadReport(@RequestParam("file") MultipartFile file) {
        try {
            // Create directory if it doesn't exist
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // Save the file
            String filename = file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.write(path, file.getBytes());

            return ResponseEntity.ok("File uploaded successfully: " + filename);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateReport(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @RequestParam(value = "useDatabase", defaultValue = "false") boolean useDatabase,
            @RequestParam(required = false) Map<String, String> parameters) {
        
        Connection connection = null;
        try {
            String jrxmlPath = UPLOAD_DIR + reportName;
            
            // Convert String parameters to proper types if needed
            Map<String, Object> reportParams = new HashMap<>();
            if (parameters != null) {
                reportParams.putAll(parameters);
            }

            // Get database connection if requested
            if (useDatabase) {
                connection = databaseConnectionService.getConnection();
            }

            // Generate report
            byte[] reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, connection);

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
            e.printStackTrace();
            return ResponseEntity.badRequest().body(("Error: " + e.getMessage()).getBytes());
        } finally {
            // Always close the connection if it was opened
            if (connection != null) {
                databaseConnectionService.closeConnection(connection);
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

    @GetMapping("/db/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isConnected = databaseConnectionService.testConnection();
            response.put("status", isConnected ? "success" : "failed");
            response.put("message", isConnected ? 
                "Database connection successful" : 
                "Database connection failed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error testing connection: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
