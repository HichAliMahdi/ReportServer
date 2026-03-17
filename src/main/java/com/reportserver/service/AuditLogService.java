package com.reportserver.service;

import com.reportserver.model.AuditLog;
import com.reportserver.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Value("${reportserver.security.audit.enabled:true}")
    private boolean enabled;

    public void logHttpRequest(HttpServletRequest request, int statusCode, long durationMs, Throwable error) {
        if (!enabled) {
            return;
        }

        AuditLog log = new AuditLog();
        log.setHttpMethod(request.getMethod());
        log.setEndpoint(request.getRequestURI());
        log.setAction(resolveAction(request));
        log.setUsername(resolveUsername(request));
        log.setStatusCode(statusCode);
        log.setSuccess(statusCode < 400 && error == null);
        log.setClientIp(resolveClientIp(request));
        log.setUserAgent(truncate(request.getHeader("User-Agent"), 500));

        String details = "durationMs=" + durationMs;
        if (error != null) {
            details += ";error=" + error.getClass().getSimpleName();
        }
        log.setDetails(truncate(details, 2000));

        auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogs(Pageable pageable, String username, String action) {
        String usernameFilter = username == null ? "" : username.trim();
        String actionFilter = action == null ? "" : action.trim();

        if (!usernameFilter.isEmpty() && !actionFilter.isEmpty()) {
            return auditLogRepository.findByUsernameContainingIgnoreCaseAndActionContainingIgnoreCaseOrderByCreatedAtDesc(
                usernameFilter,
                actionFilter,
                pageable
            );
        }

        if (!usernameFilter.isEmpty()) {
            return auditLogRepository.findByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(usernameFilter, pageable);
        }

        if (!actionFilter.isEmpty()) {
            return auditLogRepository.findByActionContainingIgnoreCaseOrderByCreatedAtDesc(actionFilter, pageable);
        }

        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String resolveUsername(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }

        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            return username.trim();
        }

        return "anonymous";
    }

    private String resolveAction(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equalsIgnoreCase(method) && "/login".equals(path)) {
            return "AUTH_LOGIN_FORM";
        }
        if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) {
            return "AUTH_LOGIN_API";
        }
        if ("POST".equalsIgnoreCase(method) && "/api/change-password".equals(path)) {
            return "USER_CHANGE_PASSWORD";
        }
        if ("POST".equalsIgnoreCase(method) && path.matches("/api/users/\\d+/reset-password")) {
            return "ADMIN_RESET_PASSWORD";
        }
        if ("POST".equalsIgnoreCase(method) && "/logout".equals(path)) {
            return "AUTH_LOGOUT";
        }

        return method + " " + path;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 64);
        }

        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
