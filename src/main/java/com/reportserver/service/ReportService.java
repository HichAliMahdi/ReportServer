package com.reportserver.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {
    
    @Autowired
    private JRDataSourceProviderService jrDataSourceProviderService;

    public byte[] generateReport(String jrxmlPath, Map<String, Object> parameters, 
                                 String outputFormat, Connection connection) throws Exception {
        return generateReportInternal(jrxmlPath, parameters, outputFormat, connection, null);
    }
    
    public byte[] generateReportWithDataSource(String jrxmlPath, Map<String, Object> parameters,
                                               String outputFormat, Object dataSource) throws Exception {
        return generateReportInternal(jrxmlPath, parameters, outputFormat, null, dataSource);
    }
    
    private byte[] generateReportInternal(String jrxmlPath, Map<String, Object> parameters, 
                                          String outputFormat, Connection connection, Object dataSource) throws Exception {
        
        File jrxmlFile = new File(jrxmlPath);
        if (!jrxmlFile.exists()) {
            throw new FileNotFoundException("Report file not found: " + jrxmlPath);
        }
        
        // Compile JRXML to report definition
        JasperReport compiledReport = JasperCompileManager.compileReport(jrxmlPath);
        
        // Fill report with data
        JasperPrint filledReport;
        if (connection != null) {
            // Use JDBC connection
            filledReport = JasperFillManager.fillReport(compiledReport, parameters, connection);
        } else if (dataSource != null) {
            // Use JRDataSource (CSV, XML, JSON, etc.)
            if (dataSource instanceof JRDataSource) {
                filledReport = JasperFillManager.fillReport(compiledReport, parameters, (JRDataSource) dataSource);
            } else if (dataSource instanceof Connection) {
                filledReport = JasperFillManager.fillReport(compiledReport, parameters, (Connection) dataSource);
            } else {
                filledReport = JasperFillManager.fillReport(compiledReport, parameters, new JREmptyDataSource());
            }
        } else {
            // Use empty data source when no connection/datasource is provided
            filledReport = JasperFillManager.fillReport(compiledReport, parameters, new JREmptyDataSource());
        }
        
        // Export based on format
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        switch (outputFormat.toLowerCase()) {
            case "pdf":
                JRPdfExporter pdfExporter = new JRPdfExporter();
                pdfExporter.setExporterInput(new SimpleExporterInput(filledReport));
                pdfExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                pdfExporter.exportReport();
                break;
                
            case "html":
                HtmlExporter htmlExporter = new HtmlExporter();
                htmlExporter.setExporterInput(new SimpleExporterInput(filledReport));
                htmlExporter.setExporterOutput(new SimpleHtmlExporterOutput(outputStream));
                htmlExporter.exportReport();
                break;
                
            case "xlsx":
                JRXlsxExporter xlsxExporter = new JRXlsxExporter();
                xlsxExporter.setExporterInput(new SimpleExporterInput(filledReport));
                xlsxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                SimpleXlsxReportConfiguration xlsxConfig = new SimpleXlsxReportConfiguration();
                xlsxConfig.setOnePagePerSheet(false);
                xlsxExporter.setConfiguration(xlsxConfig);
                xlsxExporter.exportReport();
                break;
                
            case "xls":
                JRXlsExporter xlsExporter = new JRXlsExporter();
                xlsExporter.setExporterInput(new SimpleExporterInput(filledReport));
                xlsExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                xlsExporter.exportReport();
                break;
                
            case "docx":
                JRDocxExporter docxExporter = new JRDocxExporter();
                docxExporter.setExporterInput(new SimpleExporterInput(filledReport));
                docxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                docxExporter.exportReport();
                break;
                
            case "rtf":
                JRRtfExporter rtfExporter = new JRRtfExporter();
                rtfExporter.setExporterInput(new SimpleExporterInput(filledReport));
                rtfExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                rtfExporter.exportReport();
                break;
                
            case "odt":
                JROdtExporter odtExporter = new JROdtExporter();
                odtExporter.setExporterInput(new SimpleExporterInput(filledReport));
                odtExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                odtExporter.exportReport();
                break;
                
            case "csv":
                JRCsvExporter csvExporter = new JRCsvExporter();
                csvExporter.setExporterInput(new SimpleExporterInput(filledReport));
                csvExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                csvExporter.exportReport();
                break;
                
            case "xml":
                JasperExportManager.exportReportToXmlStream(filledReport, outputStream);
                break;
                
            case "txt":
            case "text":
                JRTextExporter textExporter = new JRTextExporter();
                textExporter.setExporterInput(new SimpleExporterInput(filledReport));
                textExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                textExporter.exportReport();
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }
        
        return outputStream.toByteArray();
    }
}
