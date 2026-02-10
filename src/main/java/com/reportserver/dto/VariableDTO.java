package com.reportserver.dto;

public class VariableDTO {
    private String name;
    private String javaClass;
    private String calculation;
    private String expression;
    private String initialValue;
    private String resetType;
    private String resetGroup;
    private String incrementType;
    private String incrementGroup;
    
    public VariableDTO() {
        // Default values
        this.javaClass = "java.lang.String";
        this.calculation = "Nothing";
        this.resetType = "Report";
        this.incrementType = "None";
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
    
    public String getCalculation() {
        return calculation;
    }
    
    public void setCalculation(String calculation) {
        this.calculation = calculation;
    }
    
    public String getExpression() {
        return expression;
    }
    
    public void setExpression(String expression) {
        this.expression = expression;
    }
    
    public String getInitialValue() {
        return initialValue;
    }
    
    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }
    
    public String getResetType() {
        return resetType;
    }
    
    public void setResetType(String resetType) {
        this.resetType = resetType;
    }
    
    public String getResetGroup() {
        return resetGroup;
    }
    
    public void setResetGroup(String resetGroup) {
        this.resetGroup = resetGroup;
    }
    
    public String getIncrementType() {
        return incrementType;
    }
    
    public void setIncrementType(String incrementType) {
        this.incrementType = incrementType;
    }
    
    public String getIncrementGroup() {
        return incrementGroup;
    }
    
    public void setIncrementGroup(String incrementGroup) {
        this.incrementGroup = incrementGroup;
    }
}
