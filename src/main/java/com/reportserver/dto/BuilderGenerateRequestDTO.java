package com.reportserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BuilderGenerateRequestDTO {

    @NotBlank(message = "Report name is required")
    private String reportName;

    @NotBlank(message = "Table name is required")
    private String tableName;

    @NotEmpty(message = "At least one column must be selected")
    private List<String> columns = new ArrayList<>();

    @NotNull(message = "Datasource ID is required")
    private Long datasourceId;

    private String parametersJson;

    private String variablesJson;

    private String reportOptionsJson;

    private String reportFormat;

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getReportOptionsJson() {
        return reportOptionsJson;
    }

    public void setReportOptionsJson(String reportOptionsJson) {
        this.reportOptionsJson = reportOptionsJson;
    }

    public String getReportFormat() {
        return reportFormat;
    }

    public void setReportFormat(String reportFormat) {
        this.reportFormat = reportFormat;
    }
}
