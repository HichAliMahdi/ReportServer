package com.reportserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.ReportExecutionLog;
import com.reportserver.repository.ReportExecutionLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ReportExecutionLogService {

    @Autowired
    private ReportExecutionLogRepository reportExecutionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public ReportExecutionLog startLog(String reportName,
                                       String format,
                                       String executionType,
                                       String executedBy,
                                       Long datasourceId,
                                       Long scheduleId,
                                       Map<String, Object> parameters) {
        ReportExecutionLog log = new ReportExecutionLog();
        log.setReportName(reportName);
        log.setFormat(format);
        log.setExecutionType(executionType);
        log.setStatus("STARTED");
        log.setExecutedBy(executedBy);
        log.setDatasourceId(datasourceId);
        log.setScheduleId(scheduleId);
        log.setStartedAt(LocalDateTime.now());
        log.setParametersJson(toJson(parameters));
        return reportExecutionLogRepository.save(log);
    }

    public void markSuccess(Long logId, String outputFileName) {
        ReportExecutionLog log = reportExecutionLogRepository.findById(logId).orElse(null);
        if (log == null) {
            return;
        }
        LocalDateTime completedAt = LocalDateTime.now();
        log.setStatus("SUCCESS");
        log.setOutputFileName(outputFileName);
        log.setCompletedAt(completedAt);
        log.setDurationMs(Duration.between(log.getStartedAt(), completedAt).toMillis());
        reportExecutionLogRepository.save(log);
    }

    public void markFailed(Long logId, String errorMessage) {
        ReportExecutionLog log = reportExecutionLogRepository.findById(logId).orElse(null);
        if (log == null) {
            return;
        }
        LocalDateTime completedAt = LocalDateTime.now();
        log.setStatus("FAILED");
        log.setErrorMessage(errorMessage);
        log.setCompletedAt(completedAt);
        log.setDurationMs(Duration.between(log.getStartedAt(), completedAt).toMillis());
        reportExecutionLogRepository.save(log);
    }

    public Page<ReportExecutionLog> getAllLogs(Pageable pageable) {
        return reportExecutionLogRepository.findAllByOrderByStartedAtDesc(pageable);
    }

    public Page<ReportExecutionLog> getLogsForUser(String username, Pageable pageable) {
        return reportExecutionLogRepository.findByExecutedByOrderByStartedAtDesc(username, pageable);
    }

    private String toJson(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
