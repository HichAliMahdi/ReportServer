package com.reportserver.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            
            // Log the error
            logger.error("Error {} occurred: {} - Exception: {}", 
                statusCode, 
                message != null ? message.toString() : "No message",
                exception != null ? exception.toString() : "No exception");

            model.addAttribute("status", statusCode);
            model.addAttribute("message", message);

            // Return specific error pages based on status code
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "error/403";
            } else if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "error/404";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "error/500";
            }
        }

        // Default error page for all other errors
        return "error/error";
    }

    // Direct mapping for 403 error page
    @GetMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }

    // Direct mapping for 404 error page
    @GetMapping("/error/404")
    public String notFound() {
        return "error/404";
    }

    // Direct mapping for 500 error page
    @GetMapping("/error/500")
    public String serverError() {
        return "error/500";
    }
}
