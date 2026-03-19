package com.reportserver.dto;

import jakarta.validation.constraints.NotBlank;

public class ReportDownloadRequestDTO {

    @NotBlank(message = "Report name is required")
    private String reportName;

    @NotBlank(message = "Format is required")
    private String format = "pdf";

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
}
