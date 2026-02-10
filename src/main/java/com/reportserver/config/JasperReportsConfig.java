package com.reportserver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JasperReportsConfig {

    static {
        // Set properties as early as possible - before JasperReports initializes
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        System.setProperty("net.sf.jasperreports.default.font.name", "SansSerif");
        System.setProperty("net.sf.jasperreports.default.pdf.font.name", "Helvetica");
        
        System.out.println("======================================");
        System.out.println("JasperReports configuration initialized");
        System.out.println("Ignore missing font: " + System.getProperty("net.sf.jasperreports.awt.ignore.missing.font"));
        System.out.println("======================================");
    }
}
