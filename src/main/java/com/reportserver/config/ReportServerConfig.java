package com.reportserver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportServerConfig {

    static {
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        System.setProperty("net.sf.jasperreports.default.font.name", "SansSerif");
        System.setProperty("net.sf.jasperreports.default.pdf.font.name", "Helvetica");

        System.out.println("======================================");
        System.out.println("ReportServer reporting configuration initialized");
        System.out.println("Ignore missing font: " + System.getProperty("net.sf.jasperreports.awt.ignore.missing.font"));
        System.out.println("======================================");
    }
}
