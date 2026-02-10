package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JrxmlBuilderService {
    
    private static final Logger logger = LoggerFactory.getLogger(JrxmlBuilderService.class);

    /**
     * Generate a JRXML file content based on table and columns
     */
    public String generateJrxml(String reportName, String tableName, List<Map<String, String>> selectedColumns) {
        StringBuilder jrxml = new StringBuilder();
        
        // Calculate column widths dynamically
        int pageWidth = 802; // A4 landscape minus margins
        int columnWidth = selectedColumns.isEmpty() ? 100 : Math.max(80, pageWidth / selectedColumns.size());
        
        // Header
        jrxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        jrxml.append("<!-- Generated with JasperReports Report Builder -->\n");
        jrxml.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" ");
        jrxml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        jrxml.append("xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports ");
        jrxml.append("http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" ");
        jrxml.append("name=\"").append(reportName).append("\" ");
        jrxml.append("pageWidth=\"842\" pageHeight=\"595\" orientation=\"Landscape\" ");
        jrxml.append("columnWidth=\"802\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" ");
        jrxml.append("uuid=\"").append(UUID.randomUUID().toString()).append("\">\n");
        
        // Query String
        jrxml.append("\t<queryString language=\"SQL\">\n");
        jrxml.append("\t\t<![CDATA[SELECT ");
        
        // Build SELECT clause
        for (int i = 0; i < selectedColumns.size(); i++) {
            if (i > 0) jrxml.append(", ");
            jrxml.append("`").append(selectedColumns.get(i).get("name")).append("`");
        }
        jrxml.append(" FROM `").append(tableName).append("`]]>\n");
        jrxml.append("\t</queryString>\n");
        
        // Field Definitions
        for (Map<String, String> column : selectedColumns) {
            jrxml.append("\t<field name=\"").append(column.get("name")).append("\" ");
            jrxml.append("class=\"").append(column.get("javaClass")).append("\">\n");
            jrxml.append("\t\t<property name=\"com.jaspersoft.studio.field.name\" value=\"").append(column.get("name")).append("\"/>\n");
            jrxml.append("\t\t<property name=\"com.jaspersoft.studio.field.label\" value=\"").append(column.get("name")).append("\"/>\n");
            jrxml.append("\t\t<property name=\"com.jaspersoft.studio.field.tree.path\" value=\"").append(tableName).append("\"/>\n");
            jrxml.append("\t</field>\n");
        }
        
        // Title Band
        jrxml.append("\t<title>\n");
        jrxml.append("\t\t<band height=\"50\">\n");
        jrxml.append("\t\t\t<staticText>\n");
        jrxml.append("\t\t\t\t<reportElement x=\"0\" y=\"0\" width=\"802\" height=\"40\" ");
        jrxml.append("uuid=\"").append(UUID.randomUUID().toString()).append("\"/>\n");
        jrxml.append("\t\t\t\t<box><pen lineWidth=\"1.0\"/></box>\n");
        jrxml.append("\t\t\t\t<textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
        jrxml.append("\t\t\t\t\t<font size=\"16\" isBold=\"true\"/>\n");
        jrxml.append("\t\t\t\t</textElement>\n");
        jrxml.append("\t\t\t\t<text><![CDATA[").append(reportName).append("]]></text>\n");
        jrxml.append("\t\t\t</staticText>\n");
        jrxml.append("\t\t</band>\n");
        jrxml.append("\t</title>\n");
        
        // Column Header Band
        jrxml.append("\t<columnHeader>\n");
        jrxml.append("\t\t<band height=\"30\">\n");
        
        int xPos = 0;
        for (Map<String, String> column : selectedColumns) {
            jrxml.append("\t\t\t<staticText>\n");
            jrxml.append("\t\t\t\t<reportElement mode=\"Opaque\" x=\"").append(xPos).append("\" y=\"0\" ");
            jrxml.append("width=\"").append(columnWidth).append("\" height=\"30\" ");
            jrxml.append("backcolor=\"#CCCCCC\" uuid=\"").append(UUID.randomUUID().toString()).append("\"/>\n");
            jrxml.append("\t\t\t\t<box><pen lineWidth=\"1.0\"/></box>\n");
            jrxml.append("\t\t\t\t<textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
            jrxml.append("\t\t\t\t\t<font isBold=\"true\"/>\n");
            jrxml.append("\t\t\t\t</textElement>\n");
            jrxml.append("\t\t\t\t<text><![CDATA[").append(column.get("name")).append("]]></text>\n");
            jrxml.append("\t\t\t</staticText>\n");
            xPos += columnWidth;
        }
        
        jrxml.append("\t\t</band>\n");
        jrxml.append("\t</columnHeader>\n");
        
        // Detail Band
        jrxml.append("\t<detail>\n");
        jrxml.append("\t\t<band height=\"20\">\n");
        
        xPos = 0;
        for (Map<String, String> column : selectedColumns) {
            String javaClass = column.get("javaClass");
            String pattern = "";
            
            // Add patterns for dates and numbers
            if (javaClass.contains("Date") || javaClass.contains("Timestamp")) {
                pattern = " pattern=\"dd/MM/yyyy\"";
            } else if (javaClass.contains("Time")) {
                pattern = " pattern=\"HH:mm:ss\"";
            }
            
            jrxml.append("\t\t\t<textField").append(pattern).append(">\n");
            jrxml.append("\t\t\t\t<reportElement x=\"").append(xPos).append("\" y=\"0\" ");
            jrxml.append("width=\"").append(columnWidth).append("\" height=\"20\" ");
            jrxml.append("uuid=\"").append(UUID.randomUUID().toString()).append("\"/>\n");
            jrxml.append("\t\t\t\t<box><pen lineWidth=\"1.0\"/></box>\n");
            jrxml.append("\t\t\t\t<textElement textAlignment=\"Center\" verticalAlignment=\"Middle\"/>\n");
            jrxml.append("\t\t\t\t<textFieldExpression><![CDATA[$F{").append(column.get("name")).append("}]]></textFieldExpression>\n");
            jrxml.append("\t\t\t</textField>\n");
            xPos += columnWidth;
        }
        
        jrxml.append("\t\t</band>\n");
        jrxml.append("\t</detail>\n");
        
        // Page Footer
        jrxml.append("\t<pageFooter>\n");
        jrxml.append("\t\t<band height=\"30\">\n");
        jrxml.append("\t\t\t<textField>\n");
        jrxml.append("\t\t\t\t<reportElement x=\"700\" y=\"5\" width=\"100\" height=\"20\" ");
        jrxml.append("uuid=\"").append(UUID.randomUUID().toString()).append("\"/>\n");
        jrxml.append("\t\t\t\t<textElement textAlignment=\"Right\" verticalAlignment=\"Middle\"/>\n");
        jrxml.append("\t\t\t\t<textFieldExpression><![CDATA[\"Page \" + $V{PAGE_NUMBER}]]></textFieldExpression>\n");
        jrxml.append("\t\t\t</textField>\n");
        jrxml.append("\t\t</band>\n");
        jrxml.append("\t</pageFooter>\n");
        
        // Close jasperReport tag
        jrxml.append("</jasperReport>\n");
        
        logger.info("Generated JRXML for table {} with {} columns", tableName, selectedColumns.size());
        
        return jrxml.toString();
    }
}
