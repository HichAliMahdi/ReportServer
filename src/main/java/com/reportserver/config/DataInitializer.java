package com.reportserver.config;

import com.reportserver.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DataInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @Autowired
    private UserService userService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Check if there are any users in the database
        if (userService.countUsers() == 0) {
            // Generate a random password for the admin user
            String adminPassword = generateRandomPassword();
            
            // Create the default admin user
            userService.createUser("admin", "admin@reportserver.com", adminPassword, "ADMIN");
            
            logger.info("=".repeat(80));
            logger.info("DEFAULT ADMIN USER CREATED");
            logger.info("=".repeat(80));
            logger.info("Username: admin");
            logger.info("Password: {}", adminPassword);
            logger.info("Email: admin@reportserver.com");
            logger.info("");
            logger.info("IMPORTANT: You will be prompted to change this password on first login.");
            logger.info("=".repeat(80));
            
            // Also print to console for visibility
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DEFAULT ADMIN USER CREATED");
            System.out.println("=".repeat(80));
            System.out.println("Username: admin");
            System.out.println("Password: " + adminPassword);
            System.out.println("Email: admin@reportserver.com");
            System.out.println("");
            System.out.println("IMPORTANT: You will be prompted to change this password on first login.");
            System.out.println("=".repeat(80) + "\n");
        }
    }
    
    private String generateRandomPassword() {
        // Generate a simple but secure random password
        String uuid = UUID.randomUUID().toString();
        return "Admin@" + uuid.substring(0, 8);
    }
}
