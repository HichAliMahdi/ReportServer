package com.reportserver.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Connection;
import java.util.Map;

@Service
public class ReportService {

    public byte[] generateReport(String jrxmlPath, Map<String, Object> parameters, 
                                 String outputFormat, Connection connection) throws Exception {
        
        // Compile JRXML to JasperReport
        JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlPath);
        
        // Fill report with data
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
        
        // Export based on format
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        switch (outputFormat.toLowerCase()) {
            case "pdf":
                JRPdfExporter pdfExporter = new JRPdfExporter();
                pdfExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                pdfExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                SimplePdfExporterConfiguration pdfConfig = new SimplePdfExporterConfiguration();
                pdfExporter.setConfiguration(pdfConfig);
                pdfExporter.exportReport();
                break;
                
            case "xlsx":
            case "excel":
                JRXlsxExporter xlsxExporter = new JRXlsxExporter();
                xlsxExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                xlsxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                SimpleXlsxReportConfiguration xlsxConfig = new SimpleXlsxReportConfiguration();
                xlsxConfig.setOnePagePerSheet(false);
                xlsxExporter.setConfiguration(xlsxConfig);
                xlsxExporter.exportReport();
                break;
                
            case "html":
                JasperExportManager.exportReportToHtmlFile(jasperPrint, "temp.html");
                // For simplicity, convert to PDF if HTML is requested
                JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }
        
        return outputStream.toByteArray();
    }
}
