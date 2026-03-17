package com.reportserver.controller;

import com.reportserver.model.AsyncReportJob;
import com.reportserver.service.AsyncReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST endpoints for asynchronous report generation.
 *
 * POST  /api/async-reports          — submit a new async generation job
 * GET   /api/async-reports/{jobId}  — poll job status / download info
 * GET   /api/async-reports          — list caller's own jobs
 */
@RestController
@RequestMapping("/api/async-reports")
public class AsyncReportController {

    @Autowired
    private AsyncReportService asyncReportService;

    // ─── Submit job ───────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<Map<String, Object>> submitJob(
            @RequestParam("reportName") String reportName,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @RequestParam(value = "useDatabase", defaultValue = "false") boolean useDatabase,
            @RequestParam(value = "datasourceId", required = false) Long datasourceId,
            @RequestParam(required = false) Map<String, String> allParams) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        // Validate reportName doesn't attempt path traversal
        if (reportName.contains("..") || reportName.contains("/") || reportName.contains("\\")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid report name"));
        }

        // Strip Spring's own known @RequestParam names from the parameters map
        Map<String, Object> reportParams = new HashMap<>();
        if (allParams != null) {
                Set<String> systemParams = Set.of(
                    "reportName", "format", "useDatabase", "datasourceId");
            allParams.forEach((k, v) -> {
                if (!systemParams.contains(k)) {
                    reportParams.put(k, v);
                }
            });
        }

        String jobId = asyncReportService.submitJob(
                reportName, format, useDatabase, datasourceId, reportParams, username);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "QUEUED");
        response.put("message", "Report generation queued. Poll GET /api/async-reports/" + jobId + " for status.");
        return ResponseEntity.accepted().body(response);
    }

    // ─── Poll status ──────────────────────────────────────────────────────────

    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        AsyncReportJob job = asyncReportService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String caller = auth != null ? auth.getName() : "";

        // READ_ONLY users can only see their own jobs; ADMIN/OPERATOR can see any
        boolean isAdminOrOperator = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_OPERATOR"));
        if (!isAdminOrOperator && !caller.equals(job.getSubmittedBy())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(toMap(job));
    }

    // ─── List own jobs ────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listMyJobs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        List<Map<String, Object>> result = asyncReportService.getJobsForUser(username)
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(AsyncReportJob job) {
        Map<String, Object> map = new HashMap<>();
        map.put("jobId",         job.getJobId());
        map.put("status",        job.getStatus().name());
        map.put("reportName",    job.getReportName());
        map.put("format",        job.getFormat());
        map.put("submittedBy",   job.getSubmittedBy());
        map.put("submittedAt",   job.getSubmittedAt());
        map.put("completedAt",   job.getCompletedAt());
        map.put("outputFileName",job.getOutputFileName());
        map.put("errorMessage",  job.getErrorMessage());
        return map;
    }
}
