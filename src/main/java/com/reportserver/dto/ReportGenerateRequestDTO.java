package com.reportserver.dto;

import jakarta.validation.constraints.NotBlank;

public class ReportGenerateRequestDTO {

    @NotBlank(message = "Report name is required")
    private String reportName;

    @NotBlank(message = "Format is required")
    private String format = "pdf";

    private boolean useDatabase;

    private Long datasourceId;

    private String category;

    private String tags;

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

    public boolean isUseDatabase() {
        return useDatabase;
    }

    public void setUseDatabase(boolean useDatabase) {
        this.useDatabase = useDatabase;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
