package com.reportserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.ReportExecutionLog;
import com.reportserver.repository.ReportExecutionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportExecutionLogServiceTest {

    @Mock
    private ReportExecutionLogRepository reportExecutionLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReportExecutionLogService reportExecutionLogService;

    @Test
    void startLog_CreatesAndSavesLog() throws Exception {
        when(reportExecutionLogRepository.save(any(ReportExecutionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"k\":\"v\"}");

        ReportExecutionLog log = reportExecutionLogService.startLog(
                "sample.jrxml", "pdf", "MANUAL", "alice", 1L, 2L, Map.of("k", "v"));

        assertNotNull(log);
        assertEquals("sample.jrxml", log.getReportName());
        assertEquals("STARTED", log.getStatus());
        assertEquals("alice", log.getExecutedBy());
        assertEquals("{\"k\":\"v\"}", log.getParametersJson());
        verify(reportExecutionLogRepository).save(any(ReportExecutionLog.class));
    }

    @Test
    void markSuccess_UpdatesStatusAndDuration() {
        when(reportExecutionLogRepository.save(any(ReportExecutionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        ReportExecutionLog log = new ReportExecutionLog();
        log.setId(10L);
        log.setStatus("STARTED");
        log.setStartedAt(LocalDateTime.now().minusSeconds(2));

        when(reportExecutionLogRepository.findById(10L)).thenReturn(Optional.of(log));

        reportExecutionLogService.markSuccess(10L, "out.pdf");

        assertEquals("SUCCESS", log.getStatus());
        assertEquals("out.pdf", log.getOutputFileName());
        assertNotNull(log.getCompletedAt());
        assertNotNull(log.getDurationMs());
        assertTrue(log.getDurationMs() >= 0);
        verify(reportExecutionLogRepository).save(log);
    }

    @Test
    void markFailed_UpdatesStatusAndError() {
        when(reportExecutionLogRepository.save(any(ReportExecutionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        ReportExecutionLog log = new ReportExecutionLog();
        log.setId(11L);
        log.setStatus("STARTED");
        log.setStartedAt(LocalDateTime.now().minusSeconds(1));

        when(reportExecutionLogRepository.findById(11L)).thenReturn(Optional.of(log));

        reportExecutionLogService.markFailed(11L, "boom");

        assertEquals("FAILED", log.getStatus());
        assertEquals("boom", log.getErrorMessage());
        assertNotNull(log.getCompletedAt());
        assertNotNull(log.getDurationMs());
        verify(reportExecutionLogRepository).save(log);
    }

    @Test
    void markSuccess_WhenLogNotFound_DoesNothing() {
        when(reportExecutionLogRepository.findById(99L)).thenReturn(Optional.empty());

        reportExecutionLogService.markSuccess(99L, "ignored.pdf");

        verify(reportExecutionLogRepository, never()).save(any());
    }

    @Test
    void getAllLogs_ReturnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReportExecutionLog> page = new PageImpl<>(List.of(new ReportExecutionLog()), pageable, 1);
        when(reportExecutionLogRepository.findAllByOrderByStartedAtDesc(pageable)).thenReturn(page);

        Page<ReportExecutionLog> result = reportExecutionLogService.getAllLogs(pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void startLog_WhenJsonSerializationFails_UsesFallbackJson() throws Exception {
        when(reportExecutionLogRepository.save(any(ReportExecutionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("err"){});

        ReportExecutionLog log = reportExecutionLogService.startLog(
                "sample.jrxml", "pdf", "MANUAL", "alice", null, null, Map.of("a", 1));

        assertEquals("{}", log.getParametersJson());
    }
}
