package com.reportserver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JasperReportsConfig {

    static {
        // Set properties as early as possible - before JasperReports initializes
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        System.setProperty("net.sf.jasperreports.default.font.name", "DejaVu Sans");
        System.setProperty("net.sf.jasperreports.default.pdf.font.name", "DejaVu Sans");
        System.setProperty("net.sf.jasperreports.export.pdf.force.svg.shapes", "true");
        System.setProperty("net.sf.jasperreports.extension.registry.factory.fonts", 
            "net.sf.jasperreports.engine.fonts.SimpleFontExtensionRegistryFactory");
        System.setProperty("net.sf.jasperreports.extension.fonts.fontsfamily", "fonts.xml");
        
        System.out.println("======================================");
        System.out.println("JasperReports font configuration set");
        System.out.println("Ignore missing font: " + System.getProperty("net.sf.jasperreports.awt.ignore.missing.font"));
        System.out.println("======================================");
    }
}
