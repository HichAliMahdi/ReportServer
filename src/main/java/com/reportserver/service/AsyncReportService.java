package com.reportserver.service;

import com.reportserver.config.AsyncConfig;
import com.reportserver.model.AsyncReportJob;
import com.reportserver.model.DataSource;
import com.reportserver.model.ReportExecutionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for submitting long-running reports to a dedicated thread pool.
 *
 * Job lifecycle:
 *   submitJob()  →  QUEUED
 *   (thread picks up)  →  RUNNING
 *   success  →  SUCCESS  (outputFileName set)
 *   failure  →  FAILED   (errorMessage set)
 *
 * Job state is held in-memory (ConcurrentHashMap).
 * Permanent audit trail is written to ReportExecutionLog.
 * Old jobs are purged after reportserver.async.job-ttl-hours (default 24 h).
 */
@Service
public class AsyncReportService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncReportService.class);

    private static final String GENERATED_REPORTS_DIR = "data/generated-reports/";

    /** In-memory job registry (jobId → job). */
    private final ConcurrentHashMap<String, AsyncReportJob> jobs = new ConcurrentHashMap<>();

    @Value("${reportserver.upload-dir:data/reports/}")
    private String uploadDir;

    @Value("${reportserver.async.job-ttl-hours:24}")
    private int jobTtlHours;

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private JRDataSourceProviderService jrDataSourceProviderService;

    @Autowired
    private ReportExecutionLogService reportExecutionLogService;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Register a new job (QUEUED) and immediately hand it to the thread pool.
     *
     * @return the generated job ID (UUID)
     */
    public String submitJob(String reportName,
                            String format,
                            boolean useDatabase,
                            Long datasourceId,
                            Map<String, Object> reportParams,
                            String submittedBy) {

        String jobId = UUID.randomUUID().toString();
        AsyncReportJob job = new AsyncReportJob(jobId, reportName, format, submittedBy);
        jobs.put(jobId, job);

        // Fire-and-forget asynchronous execution
        executeAsync(job, reportName, format, useDatabase, datasourceId, reportParams, submittedBy);

        logger.info("Async job {} queued: report={} format={} user={}", jobId, reportName, format, submittedBy);
        return jobId;
    }

    /**
     * Look up a job by ID.
     *
     * @return the job, or null if unknown / already purged
     */
    public AsyncReportJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Return all jobs belonging to a specific user, newest first.
     */
    public List<AsyncReportJob> getJobsForUser(String username) {
        List<AsyncReportJob> result = new ArrayList<>();
        for (AsyncReportJob j : jobs.values()) {
            if (username.equals(j.getSubmittedBy())) {
                result.add(j);
            }
        }
        result.sort(Comparator.comparing(AsyncReportJob::getSubmittedAt).reversed());
        return result;
    }

    // ─── Async execution ──────────────────────────────────────────────────────

    @Async(AsyncConfig.REPORT_EXECUTOR)
    protected void executeAsync(AsyncReportJob job,
                                String reportName,
                                String format,
                                boolean useDatabase,
                                Long datasourceId,
                                Map<String, Object> reportParams,
                                String submittedBy) {

        String jrxmlPath = uploadDir + reportName;
        job.markRunning();

        Long logId = null;
        try {
            ReportExecutionLog log = reportExecutionLogService.startLog(
                    reportName, format, "ASYNC", submittedBy, datasourceId, null, reportParams);
            logId = log.getId();

            // ── Resolve data source ───────────────────────────────────────────
            Object dataSource = null;
            if (useDatabase && datasourceId != null) {
                DataSource ds = dataSourceService.getDataSourceById(datasourceId)
                        .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));
                dataSource = jrDataSourceProviderService.getDataSource(ds, reportParams);
                if (dataSource == null) {
                    throw new IllegalArgumentException("Failed to create data source for id: " + datasourceId);
                }
            }

            // ── Generate report ────────────────────────────────────────────────
            byte[] reportBytes;
            if (dataSource instanceof Connection) {
                reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, (Connection) dataSource);
            } else if (dataSource != null) {
                reportBytes = reportService.generateReportWithDataSource(jrxmlPath, reportParams, format, dataSource);
            } else {
                reportBytes = reportService.generateReport(jrxmlPath, reportParams, format, null);
            }

            // ── Persist to disk ───────────────────────────────────────────────
            String ext = getExtension(format);
            String fileName = reportName.replace(".jrxml", "") + "_" + System.currentTimeMillis() + "." + ext;
            String filePath = GENERATED_REPORTS_DIR + fileName;

            File dir = new File(GENERATED_REPORTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(reportBytes);
            }

            reportExecutionLogService.markSuccess(logId, fileName);
            job.markSuccess(fileName);
            logger.info("Async job {} completed: file={}", job.getJobId(), fileName);

        } catch (Exception e) {
            logger.error("Async job {} failed: {}", job.getJobId(), e.getMessage(), e);
            if (logId != null) {
                reportExecutionLogService.markFailed(logId, e.getMessage());
            }
            job.markFailed(e.getMessage());
        }
    }

    // ─── Scheduled cleanup ────────────────────────────────────────────────────

    /**
     * Remove completed / failed jobs older than the configured TTL.
     * Runs every hour on the hour.
     */
    @Scheduled(fixedRateString = "${reportserver.async.cleanup-rate-ms:3600000}")
    public void cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(jobTtlHours);
        int removed = 0;
        Iterator<Map.Entry<String, AsyncReportJob>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AsyncReportJob> entry = it.next();
            AsyncReportJob job = entry.getValue();
            if (job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Purged {} completed async report jobs older than {} hours", removed, jobTtlHours);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getExtension(String format) {
        return switch (format.toLowerCase()) {
            case "xlsx" -> "xlsx";
            case "xls"  -> "xls";
            case "docx" -> "docx";
            case "html" -> "html";
            case "csv"  -> "csv";
            case "xml"  -> "xml";
            case "rtf"  -> "rtf";
            case "odt"  -> "odt";
            case "txt", "text" -> "txt";
            default     -> "pdf";
        };
    }
}
