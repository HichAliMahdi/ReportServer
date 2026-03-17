package com.reportserver.security;

import com.reportserver.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {

    @Autowired
    private AuditLogService auditLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/css/") ||
            path.startsWith("/js/") ||
            path.startsWith("/images/") ||
            path.startsWith("/webjars/") ||
            path.startsWith("/favicon") ||
            path.startsWith("/actuator/health")) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }

        if ("GET".equalsIgnoreCase(method)) {
            return path.startsWith("/api/auth") == false &&
                path.startsWith("/login") == false &&
                path.startsWith("/logout") == false;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        Throwable error = null;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            error = ex;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = error == null ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            auditLogService.logHttpRequest(request, status, duration, error);
        }
    }
}
