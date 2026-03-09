package com.reportserver.controller;

import com.reportserver.model.ReportExecutionLog;
import com.reportserver.service.ReportExecutionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/report-executions")
public class ReportExecutionLogController {

    @Autowired
    private ReportExecutionLogService reportExecutionLogService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    @GetMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(resolvedSize, 1));

        boolean isAdmin = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Page<ReportExecutionLog> logs = isAdmin
                ? reportExecutionLogService.getAllLogs(pageable)
                : reportExecutionLogService.getLogsForUser(authentication.getName(), pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", logs.getContent());
        response.put("page", logs.getNumber());
        response.put("size", logs.getSize());
        response.put("totalElements", logs.getTotalElements());
        response.put("totalPages", logs.getTotalPages());
        response.put("first", logs.isFirst());
        response.put("last", logs.isLast());
        return ResponseEntity.ok(response);
    }
}
