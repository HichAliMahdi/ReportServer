package com.reportserver.dto;

public class ParameterDTO {
    private String name;
    private String javaClass;
    private String defaultValueExpression;
    private String description;
    
    public ParameterDTO() {
        // Default values
        this.javaClass = "java.lang.String";
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getJavaClass() {
        return javaClass;
    }
    
    public void setJavaClass(String javaClass) {
        this.javaClass = javaClass;
    }
    
    public String getDefaultValueExpression() {
        return defaultValueExpression;
    }
    
    public void setDefaultValueExpression(String defaultValueExpression) {
        this.defaultValueExpression = defaultValueExpression;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
