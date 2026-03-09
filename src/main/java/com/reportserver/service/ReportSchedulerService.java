package com.reportserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.ReportExecutionLog;
import com.reportserver.model.ScheduledReport;
import com.reportserver.repository.ScheduledReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulerService.class);
    private static final String OUTPUT_DIR = "data/scheduled_output/";

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    @Autowired
    private ScheduledReportRepository scheduledReportRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private JRDataSourceProviderService jrDataSourceProviderService;

    @Autowired
    private ScheduledReportService scheduledReportService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportExecutionLogService reportExecutionLogService;

    private void executeScheduledReport(ScheduledReport scheduledReport) {
            @Autowired
            private ReportDeliveryService reportDeliveryService;

            private void executeScheduledReport(ScheduledReport scheduledReport) {
        logger.info("Executing scheduled report: {}", scheduledReport.getName());
            @Autowired
            private ReportDeliveryService reportDeliveryService;

            private void executeScheduledReport(ScheduledReport scheduledReport) {
                logger.info("Executing scheduled report: {}", scheduledReport.getName());
        Long logId = null;
    @Autowired
    private ReportDeliveryService reportDeliveryService;

    private void executeScheduledReport(ScheduledReport scheduledReport) {
        logger.info("Executing scheduled report: {}", scheduledReport.getName());
        Long logId = null;

        try {
            // Get report file path
            String jrxmlPath = this.uploadDir + scheduledReport.getReportName();
            File reportFile = new File(jrxmlPath);
            
            if (!reportFile.exists()) {
                logger.error("Report file not found: {}", jrxmlPath);
                return;
            }

            // Parse parameters from JSON first
            Map<String, Object> parameters = new HashMap<>();
            if (scheduledReport.getParameters() != null && !scheduledReport.getParameters().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = objectMapper.readValue(scheduledReport.getParameters(), Map.class);
                    parameters.putAll(params);
                } catch (Exception e) {
                    logger.warn("Could not parse parameters for report: {}", scheduledReport.getName(), e);
                }
            }

            ReportExecutionLog log = reportExecutionLogService.startLog(
                    scheduledReport.getReportName(),
                    scheduledReport.getFormat(),
                    "SCHEDULED",
                    scheduledReport.getCreatedBy(),
                    scheduledReport.getDatasourceId(),
                    scheduledReport.getId(),
                    parameters
            );
            logId = log.getId();

            // Get database connection or datasource if needed
            Object dataSource = null;
            if (scheduledReport.getDatasourceId() != null) {
                try {
                    com.reportserver.model.DataSource ds = dataSourceService.getDataSourceById(scheduledReport.getDatasourceId())
                            .orElseThrow(() -> new RuntimeException("Datasource not found"));
                    dataSource = jrDataSourceProviderService.getDataSource(ds, parameters);
                } catch (Exception e) {
                    logger.warn("Could not get datasource for ID: {}, proceeding with empty datasource", 
                               scheduledReport.getDatasourceId(), e);
                }
            }

            // Generate report
            byte[] reportData;
            if (dataSource instanceof Connection) {
                reportData = reportService.generateReport(jrxmlPath, parameters, scheduledReport.getFormat(), (Connection) dataSource);
            } else {
                reportData = reportService.generateReportWithDataSource(jrxmlPath, parameters, scheduledReport.getFormat(), dataSource);
            }

            // Close connection if it was opened
            if (dataSource instanceof Connection) {
                try {
                    ((Connection) dataSource).close();
                } catch (Exception e) {
                    logger.warn("Error closing database connection", e);
                                }
            }

            // Save report to output directory
            String outputFileName = saveScheduledReport(scheduledReport, reportData);

            // Update last run time and calculate next run time
            scheduledReportService.updateLastRunTime(scheduledReport.getId(), LocalDateTime.now());
            if (logId != null) {
                reportExecutionLogService.markSuccess(logId, outputFileName);
                        // Deliver report via configured channels (email, webhook)
                        try {
                            String outputBasePath = scheduledReport.getOutputPath() != null && !scheduledReport.getOutputPath().isEmpty()
                                    ? scheduledReport.getOutputPath()
                                    : OUTPUT_DIR;

                            File reportFile = new File(Paths.get(outputBasePath, outputFileName).toString());
                            reportDeliveryService.deliverScheduledReport(
                                scheduledReport.getId(),
                                scheduledReport.getReportName(),
                                reportFile,
                                scheduledReport.getFormat()
                            );
                        } catch (Exception e) {
                            logger.warn("Failed to deliver scheduled report: {}", e.getMessage(), e);
                            // Non-fatal; report execution succeeded but delivery failed
                        }
            }

            logger.info("Successfully executed scheduled report: {}", scheduledReport.getName());

        } catch (Exception e) {
            logger.error("Error executing scheduled report: " + scheduledReport.getName(), e);
            if (logId != null) {
                reportExecutionLogService.markFailed(logId, e.getMessage());
            }
            
            // Still update the next run time even if there was an error
            try {
                scheduledReportService.updateLastRunTime(scheduledReport.getId(), LocalDateTime.now());
            } catch (Exception ex) {
                logger.error("Error updating last run time", ex);
            }
        }
    }

    private String saveScheduledReport(ScheduledReport scheduledReport, byte[] reportData) throws Exception {
        // Create output directory if it doesn't exist
        String outputBasePath = scheduledReport.getOutputPath() != null && !scheduledReport.getOutputPath().isEmpty()
                ? scheduledReport.getOutputPath()
                : OUTPUT_DIR;
        
        File outputDir = new File(outputBasePath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Generate filename with timestamp
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String reportBaseName = scheduledReport.getReportName().replace(".jrxml", "");
        String filename = String.format("%s_%s.%s",
                reportBaseName,
                timestamp,
                scheduledReport.getFormat().toLowerCase());

        // Save file
        Path outputPath = Paths.get(outputBasePath, filename);
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            fos.write(reportData);
        }

        logger.info("Saved scheduled report to: {}", outputPath.toString());
        return filename;
    }

    // Manual execution for testing or on-demand runs
    public void executeScheduledReportNow(Long scheduledReportId) {
        ScheduledReport scheduledReport = scheduledReportRepository.findById(scheduledReportId)
                .orElseThrow(() -> new RuntimeException("Scheduled report not found: " + scheduledReportId));
        
        executeScheduledReport(scheduledReport);
    }
}
