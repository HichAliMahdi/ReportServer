package com.reportserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        logger.warn("Access denied for user attempting to access: {}", request.getRequestURI());
        
        ModelAndView mav = new ModelAndView();
        mav.setViewName("error/403");
        mav.addObject("message", "You don't have permission to access this resource.");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneralException(Exception ex, HttpServletRequest request, Model model) {
        logger.error("Unexpected error occurred while accessing: {}", request.getRequestURI(), ex);
        
        ModelAndView mav = new ModelAndView();
        mav.setViewName("error/error");
        mav.addObject("status", 500);
        mav.addObject("message", "An unexpected error occurred. Please try again later.");
        return mav;
    }
}
