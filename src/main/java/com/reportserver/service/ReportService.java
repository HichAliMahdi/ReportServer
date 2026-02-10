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
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    public byte[] generateReport(String jrxmlPath, Map<String, Object> parameters, 
                                 String outputFormat, Connection connection) throws Exception {
        
        File jrxmlFile = new File(jrxmlPath);
        if (!jrxmlFile.exists()) {
            throw new FileNotFoundException("Report file not found: " + jrxmlPath);
        }
        
        // Compile JRXML to JasperReport
        JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlPath);
        
        // Fill report with data (use empty data source if no connection provided)
        JasperPrint jasperPrint;
        if (connection != null) {
            jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
        } else {
            // Use empty data source when no connection is provided
            jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
        }
        
        // Export based on format
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        switch (outputFormat.toLowerCase()) {
            case "pdf":
                JRPdfExporter pdfExporter = new JRPdfExporter();
                pdfExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                pdfExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                pdfExporter.exportReport();
                break;
                
            case "html":
                HtmlExporter htmlExporter = new HtmlExporter();
                htmlExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                htmlExporter.setExporterOutput(new SimpleHtmlExporterOutput(outputStream));
                htmlExporter.exportReport();
                break;
                
            case "xlsx":
                JRXlsxExporter xlsxExporter = new JRXlsxExporter();
                xlsxExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                xlsxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                SimpleXlsxReportConfiguration xlsxConfig = new SimpleXlsxReportConfiguration();
                xlsxConfig.setOnePagePerSheet(false);
                xlsxExporter.setConfiguration(xlsxConfig);
                xlsxExporter.exportReport();
                break;
                
            case "xls":
                JRXlsExporter xlsExporter = new JRXlsExporter();
                xlsExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                xlsExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                xlsExporter.exportReport();
                break;
                
            case "docx":
                JRDocxExporter docxExporter = new JRDocxExporter();
                docxExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                docxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                docxExporter.exportReport();
                break;
                
            case "rtf":
                JRRtfExporter rtfExporter = new JRRtfExporter();
                rtfExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                rtfExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                rtfExporter.exportReport();
                break;
                
            case "odt":
                JROdtExporter odtExporter = new JROdtExporter();
                odtExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                odtExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                odtExporter.exportReport();
                break;
                
            case "csv":
                JRCsvExporter csvExporter = new JRCsvExporter();
                csvExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                csvExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                csvExporter.exportReport();
                break;
                
            case "xml":
                JasperExportManager.exportReportToXmlStream(jasperPrint, outputStream);
                break;
                
            case "txt":
            case "text":
                JRTextExporter textExporter = new JRTextExporter();
                textExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                textExporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
                textExporter.exportReport();
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }
        
        return outputStream.toByteArray();
    }
}
