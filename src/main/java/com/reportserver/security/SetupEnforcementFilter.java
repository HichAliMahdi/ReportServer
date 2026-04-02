package com.reportserver.security;

import com.reportserver.service.InstallationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SetupEnforcementFilter extends OncePerRequestFilter {

    private final InstallationService installationService;

    public SetupEnforcementFilter(InstallationService installationService) {
        this.installationService = installationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (installationService.isSetupComplete()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isAllowedDuringSetup(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Application setup is required before using the API\"}");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/setup");
    }

    private boolean isAllowedDuringSetup(String path) {
        return path.startsWith("/setup")
                || path.startsWith("/api/setup")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/error")
                || path.startsWith("/actuator/health")
                || path.startsWith("/h2-console")
                || path.equals("/favicon.ico");
    }
}
