package com.reportserver.config;

import com.reportserver.model.User;
import com.reportserver.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class FirstLoginFilter extends OncePerRequestFilter {
    
    private final ApplicationContext applicationContext;
    
    public FirstLoginFilter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Skip filter if not authenticated or for specific paths
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestUri = request.getRequestURI();
        
        // Skip filter for these paths
        if (requestUri.equals("/change-password") || 
            requestUri.equals("/api/change-password") ||
            requestUri.equals("/logout") ||
            requestUri.startsWith("/css") ||
            requestUri.startsWith("/js") ||
            requestUri.startsWith("/images")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check if user needs to change password
        String username = auth.getName();
        UserService userService = applicationContext.getBean(UserService.class);
        Optional<User> userOpt = userService.findByUsername(username);
        
        if (userOpt.isPresent() && userOpt.get().isFirstLogin()) {
            // Redirect to change password page
            response.sendRedirect("/change-password");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
