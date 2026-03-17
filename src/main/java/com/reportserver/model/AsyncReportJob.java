package com.reportserver.model;

import java.time.LocalDateTime;

/**
 * In-memory representation of an asynchronous report generation job.
 * Stored in a ConcurrentHashMap inside AsyncReportService; not persisted in the DB
 * (the ReportExecutionLog covers the permanent audit trail).
 */
public class AsyncReportJob {

    public enum Status { QUEUED, RUNNING, SUCCESS, FAILED }

    private final String jobId;
    private volatile Status status;
    private final String reportName;
    private final String format;
    private final String submittedBy;
    private final LocalDateTime submittedAt;
    private volatile LocalDateTime completedAt;
    private volatile String outputFileName;
    private volatile String errorMessage;

    public AsyncReportJob(String jobId, String reportName, String format, String submittedBy) {
        this.jobId = jobId;
        this.reportName = reportName;
        this.format = format;
        this.submittedBy = submittedBy;
        this.submittedAt = LocalDateTime.now();
        this.status = Status.QUEUED;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public String getJobId()          { return jobId; }
    public Status getStatus()         { return status; }
    public String getReportName()     { return reportName; }
    public String getFormat()         { return format; }
    public String getSubmittedBy()    { return submittedBy; }
    public LocalDateTime getSubmittedAt()  { return submittedAt; }
    public LocalDateTime getCompletedAt()  { return completedAt; }
    public String getOutputFileName() { return outputFileName; }
    public String getErrorMessage()   { return errorMessage; }

    // ─── Setters (service-internal use only) ─────────────────────────────────

    public void markRunning() {
        this.status = Status.RUNNING;
    }

    public void markSuccess(String fileName) {
        this.outputFileName = fileName;
        this.completedAt = LocalDateTime.now();
        this.status = Status.SUCCESS;
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        this.status = Status.FAILED;
    }
}
