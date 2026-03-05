package com.reportserver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportServerConfig {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ReportServerConfig.class);

    static {
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        System.setProperty("net.sf.jasperreports.default.font.name", "SansSerif");
        System.setProperty("net.sf.jasperreports.default.pdf.font.name", "Helvetica");
    }

    public ReportServerConfig() {
        logger.info("======================================");
        logger.info("ReportServer reporting configuration initialized");
        logger.info("Ignore missing font: {}", System.getProperty("net.sf.jasperreports.awt.ignore.missing.font"));
        logger.info("======================================");
    }
}
