package com.reportserver.dto;

import com.reportserver.model.DataSource;
import java.time.LocalDateTime;

/**
 * DTO for DataSource that excludes sensitive information like passwords
 */
public class DataSourceDTO {
    
    private Long id;
    private String name;
    private String url;
    private String username;
    private String driverClassName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public DataSourceDTO() {}
    
    public DataSourceDTO(DataSource dataSource) {
        this.id = dataSource.getId();
        this.name = dataSource.getName();
        this.url = dataSource.getUrl();
        this.username = dataSource.getUsername();
        this.driverClassName = dataSource.getDriverClassName();
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
