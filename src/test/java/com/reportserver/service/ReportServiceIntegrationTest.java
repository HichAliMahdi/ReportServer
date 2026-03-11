package com.reportserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JasperReports generation engine via ReportService.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportServiceIntegrationTest {

    @Autowired
    private ReportService reportService;

    @TempDir
    Path tempDir;

    private Path reportFile;

    @BeforeEach
    void setUp() throws Exception {
        String jrxmlContent = """
<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports
              http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
              name="SimpleReport" pageWidth="595" pageHeight="842" columnWidth="555"
              leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
    <parameter name="ReportTitle" class="java.lang.String"/>
    <title>
        <band height="50">
            <staticText>
                <reportElement x="0" y="0" width="555" height="30"/>
                <textElement textAlignment="Center">
                    <font size="18" isBold="true"/>
                </textElement>
                <text><![CDATA[Test Report]]></text>
            </staticText>
        </band>
    </title>
    <detail>
        <band height="30">
            <textField>
                <reportElement x="0" y="0" width="555" height="30"/>
                <textFieldExpression><![CDATA[$P{ReportTitle}]]></textFieldExpression>
            </textField>
        </band>
    </detail>
</jasperReport>
""";
        reportFile = tempDir.resolve("test_report.jrxml");
        Files.writeString(reportFile, jrxmlContent);
    }

    @Test
    void testGenerateReport_PDF() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ReportTitle", "Integration Test Report");

        byte[] pdfData = reportService.generateReport(reportFile.toString(), parameters, "pdf", null);

        assertNotNull(pdfData);
        assertTrue(pdfData.length > 0);
        assertEquals("%PDF", new String(pdfData, 0, 4), "Output should be a valid PDF");
    }

    @Test
    void testGenerateReport_HTML() throws Exception {
        byte[] htmlData = reportService.generateReport(reportFile.toString(), new HashMap<>(), "html", null);

        assertNotNull(htmlData);
        assertTrue(htmlData.length > 0);
        String html = new String(htmlData);
        assertTrue(html.contains("<html") || html.contains("<!DOCTYPE"), "Output should be HTML");
    }

    @Test
    void testGenerateReport_InvalidFormat_ThrowsException() {
        assertThrows(Exception.class, () ->
            reportService.generateReport(reportFile.toString(), new HashMap<>(), "unsupported_format", null));
    }

    @Test
    void testGenerateReport_MissingFile_ThrowsException() {
        assertThrows(FileNotFoundException.class, () ->
            reportService.generateReport(
                tempDir.resolve("does_not_exist.jrxml").toString(),
                new HashMap<>(), "pdf", null));
    }

    @Test
    void testGenerateReport_WithNullDataSource() throws Exception {
        byte[] pdfData = reportService.generateReportWithDataSource(
            reportFile.toString(), new HashMap<>(), "pdf", null);

        assertNotNull(pdfData);
        assertTrue(pdfData.length > 0);
    }

    @Test
    void testGenerateReport_MultipleFormats() throws Exception {
        String[] formats = {"pdf", "html", "xlsx", "csv"};
        for (String format : formats) {
            byte[] data = reportService.generateReport(reportFile.toString(), new HashMap<>(), format, null);
            assertNotNull(data, "Report data must not be null for format: " + format);
            assertTrue(data.length > 0, "Report data must not be empty for format: " + format);
        }
    }
}
