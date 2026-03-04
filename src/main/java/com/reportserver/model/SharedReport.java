package com.reportserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shared_reports")
public class SharedReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String reportFileName;
    
    @Column(nullable = false)
    private String reportName;
    
    @Column(nullable = false)
    private String reportFormat; // pdf, csv, xlsx, etc
    
    @Column(name = "shared_with_readonly", nullable = false)
    private boolean sharedWithReadOnly = false;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "shared_at")
    private LocalDateTime sharedAt;
    
    @Column(name = "shared_by")
    private String sharedBy;
    
    public SharedReport() {
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getReportFileName() {
        return reportFileName;
    }
    
    public void setReportFileName(String reportFileName) {
        this.reportFileName = reportFileName;
    }
    
    public String getReportName() {
        return reportName;
    }
    
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }
    
    public String getReportFormat() {
        return reportFormat;
    }
    
    public void setReportFormat(String reportFormat) {
        this.reportFormat = reportFormat;
    }
    
    public boolean isSharedWithReadOnly() {
        return sharedWithReadOnly;
    }
    
    public void setSharedWithReadOnly(boolean sharedWithReadOnly) {
        this.sharedWithReadOnly = sharedWithReadOnly;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getSharedAt() {
        return sharedAt;
    }
    
    public void setSharedAt(LocalDateTime sharedAt) {
        this.sharedAt = sharedAt;
    }
    
    public String getSharedBy() {
        return sharedBy;
    }
    
    public void setSharedBy(String sharedBy) {
        this.sharedBy = sharedBy;
    }
}
