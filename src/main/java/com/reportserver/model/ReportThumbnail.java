package com.reportserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_thumbnails", indexes = {
    @Index(name = "idx_report_template_id", columnList = "report_template_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class ReportThumbnail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reportTemplateId;

    @Column(nullable = false, length = 500)
    private String thumbnailPath; // Relative path: /thumbnails/report-name-thumbnail.png

    @Column(nullable = false)
    private Long fileSize; // bytes

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime generatedAt; // Timestamp of report generation used to create thumbnail

    @Column(nullable = false)
    private Boolean valid = true; // Set to false if thumbnail becomes corrupted

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ReportThumbnail() {}

    public ReportThumbnail(Long reportTemplateId, String thumbnailPath) {
        this.reportTemplateId = reportTemplateId;
        this.thumbnailPath = thumbnailPath;
        this.generatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReportTemplateId() { return reportTemplateId; }
    public void setReportTemplateId(Long reportTemplateId) { this.reportTemplateId = reportTemplateId; }

    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }
}
