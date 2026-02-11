package com.reportserver.dto;

import com.reportserver.model.DataSource;
import com.reportserver.model.DataSourceType;
import java.time.LocalDateTime;

/**
 * DTO for DataSource that excludes sensitive information like passwords
 */
public class DataSourceDTO {
    
    private Long id;
    private String name;
    private DataSourceType type;
    private String url;
    private String username;
    private String driverClassName;
    private String filePath;
    private String configuration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public DataSourceDTO() {}
    
    public DataSourceDTO(DataSource dataSource) {
        this.id = dataSource.getId();
        this.name = dataSource.getName();
        this.type = dataSource.getType();
        this.url = dataSource.getUrl();
        this.username = dataSource.getUsername();
        this.driverClassName = dataSource.getDriverClassName();
        this.filePath = dataSource.getFilePath();
        this.configuration = dataSource.getConfiguration();
        this.createdAt = dataSource.getCreatedAt();
        this.updatedAt = dataSource.getUpdatedAt();
    }
    
    public static DataSourceDTO fromEntity(DataSource dataSource) {
        return new DataSourceDTO(dataSource);
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
    
    public DataSourceType getType() {
        return type;
    }
    
    public void setType(DataSourceType type) {
        this.type = type;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getDriverClassName() {
        return driverClassName;
    }
    
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(String configuration) {
        this.configuration = configuration;
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
