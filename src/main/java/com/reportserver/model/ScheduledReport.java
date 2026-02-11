package com.reportserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "scheduled_reports")
public class ScheduledReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String reportName;
    
    @Column(nullable = false)
    private String format;
    
    @Column(nullable = false)
    private String scheduleType; // HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
    
    @Column
    private Long datasourceId;
    
    @Column
    private Boolean enabled = true;
    
    @Column
    private LocalDateTime lastRunTime;
    
    @Column
    private LocalDateTime nextRunTime;
    
    @Column
    private String outputPath; // Where to save generated reports
    
    @Column(length = 2000)
    private String description;
    
    // Store report parameters as JSON string
    @Column(length = 4000)
    private String parameters;
    
    // For weekly schedule - day of week (1-7, 1=Monday)
    @Column
    private Integer dayOfWeek;
    
    // For monthly/yearly schedule - day of month (1-31)
    @Column
    private Integer dayOfMonth;
    
    // For yearly schedule - month (1-12)
    @Column
    private Integer monthOfYear;
    
    // For hourly/daily schedule - hour of day (0-23)
    @Column
    private Integer hourOfDay;
    
    // For all schedules - minute of hour (0-59)
    @Column
    private Integer minuteOfHour = 0;
    
    @Column
    private String createdBy;
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getReportName() {
        return reportName;
    }
    
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public String getScheduleType() {
        return scheduleType;
    }
    
    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }
    
    public Long getDatasourceId() {
        return datasourceId;
    }
    
    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }
    
    public void setLastRunTime(LocalDateTime lastRunTime) {
        this.lastRunTime = lastRunTime;
    }
    
    public LocalDateTime getNextRunTime() {
        return nextRunTime;
    }
    
    public void setNextRunTime(LocalDateTime nextRunTime) {
        this.nextRunTime = nextRunTime;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getParameters() {
        return parameters;
    }
    
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
    
    public Integer getDayOfWeek() {
        return dayOfWeek;
    }
    
    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }
    
    public Integer getDayOfMonth() {
        return dayOfMonth;
    }
    
    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }
    
    public Integer getMonthOfYear() {
        return monthOfYear;
    }
    
    public void setMonthOfYear(Integer monthOfYear) {
        this.monthOfYear = monthOfYear;
    }
    
    public Integer getHourOfDay() {
        return hourOfDay;
    }
    
    public void setHourOfDay(Integer hourOfDay) {
        this.hourOfDay = hourOfDay;
    }
    
    public Integer getMinuteOfHour() {
        return minuteOfHour;
    }
    
    public void setMinuteOfHour(Integer minuteOfHour) {
        this.minuteOfHour = minuteOfHour;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
