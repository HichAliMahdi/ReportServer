package com.reportserver.service;

import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JrxmlBuilderService {
    
    private static final Logger logger = LoggerFactory.getLogger(JrxmlBuilderService.class);

    /**
     * Generate a JRXML file content based on table, columns, parameters, and variables
     */
    public String generateJrxml(String reportName, String tableName, List<Map<String, String>> selectedColumns, 
                                 List<ParameterDTO> parameters, List<VariableDTO> variables) {
        StringBuilder jrxml = new StringBuilder();
        
        // Calculate column widths dynamically
        int pageWidth = 802; // A4 landscape minus margins
        int columnWidth = selectedColumns.isEmpty() ? 100 : Math.max(80, pageWidth / selectedColumns.size());
        
        // Header
        jrxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        jrxml.append("<!-- Generated with ReportServer Report Builder -->\n");
        jrxml.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" ");
        jrxml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        jrxml.append("xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports ");
        jrxml.append("http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" ");
        jrxml.append("name=\"").append(reportName).append("\" ");
        jrxml.append("pageWidth=\"842\" pageHeight=\"595\" orientation=\"Landscape\" ");
        jrxml.append("columnWidth=\"802\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" ");
        jrxml.append("uuid=\"").append(UUID.randomUUID().toString()).append("\">\n");
        
        // Parameter Definitions (must come BEFORE queryString per report XSD)
        if (parameters != null && !parameters.isEmpty()) {
            logger.info("Adding {} parameter(s) to JRXML", parameters.size());
            for (ParameterDTO parameter : parameters) {
                jrxml.append("\t<parameter name=\"").append(parameter.getName()).append("\" ");
                jrxml.append("class=\"").append(parameter.getJavaClass()).append("\">\n");
                
                // Add default value expression if provided
                if (parameter.getDefaultValueExpression() != null && !parameter.getDefaultValueExpression().trim().isEmpty()) {
                    jrxml.append("\t\t<defaultValueExpression><![CDATA[").append(parameter.getDefaultValueExpression()).append("]]></defaultValueExpression>\n");
                }
                
                jrxml.append("\t</parameter>\n");
            }
        }
        
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
        
        // Variable Definitions
        if (variables != null && !variables.isEmpty()) {
            logger.info("Adding {} variable(s) to JRXML", variables.size());
            for (VariableDTO variable : variables) {
                jrxml.append("\t<variable name=\"").append(variable.getName()).append("\" ");
                jrxml.append("class=\"").append(variable.getJavaClass()).append("\" ");
                
                // Add calculation type if not "Nothing"
                if (variable.getCalculation() != null && !variable.getCalculation().equals("Nothing")) {
                    jrxml.append("calculation=\"").append(variable.getCalculation()).append("\" ");
                }
                
                // Add reset type
                if (variable.getResetType() != null && !variable.getResetType().equals("Report")) {
                    jrxml.append("resetType=\"").append(variable.getResetType()).append("\" ");
                    // Add reset group if reset type is Group
                    if (variable.getResetType().equals("Group") && variable.getResetGroup() != null && !variable.getResetGroup().isEmpty()) {
                        jrxml.append("resetGroup=\"").append(variable.getResetGroup()).append("\" ");
                    }
                }
                
                // Add increment type if not "None"
                if (variable.getIncrementType() != null && !variable.getIncrementType().equals("None")) {
                    jrxml.append("incrementType=\"").append(variable.getIncrementType()).append("\" ");
                    // Add increment group if increment type is Group
                    if (variable.getIncrementType().equals("Group") && variable.getIncrementGroup() != null && !variable.getIncrementGroup().isEmpty()) {
                        jrxml.append("incrementGroup=\"").append(variable.getIncrementGroup()).append("\" ");
                    }
                }
                
                jrxml.append(">\n");
                
                // Add initial value expression if provided
                if (variable.getInitialValue() != null && !variable.getInitialValue().trim().isEmpty()) {
                    jrxml.append("\t\t<initialValueExpression><![CDATA[").append(variable.getInitialValue()).append("]]></initialValueExpression>\n");
                }
                
                // Add variable expression if provided
                if (variable.getExpression() != null && !variable.getExpression().trim().isEmpty()) {
                    jrxml.append("\t\t<variableExpression><![CDATA[").append(variable.getExpression()).append("]]></variableExpression>\n");
                }
                
                jrxml.append("\t</variable>\n");
            }
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
        
        // Close report root tag
        jrxml.append("</jasperReport>\n");
        
        logger.info("Generated JRXML for table {} with {} columns", tableName, selectedColumns.size());
        
        return jrxml.toString();
    }

    public String applySharedCoverToBuilderJrxml(String jrxmlContent, String reportName, Map<String, Object> reportOptions) {
        if (jrxmlContent == null || jrxmlContent.isBlank() || reportOptions == null || reportOptions.isEmpty()) {
            return jrxmlContent;
        }

        boolean coverPageEnabled = asBoolean(reportOptions.get("coverPageEnabled"));
        if (!coverPageEnabled) {
            return jrxmlContent;
        }

        String coverTitle = asString(reportOptions.get("coverTitle"));
        String coverSubtitle = asString(reportOptions.get("coverSubtitle"));
        String coverAuthor = asString(reportOptions.get("coverAuthor"));
        boolean coverDateEnabled = asBoolean(reportOptions.get("coverDateEnabled"));
        boolean coverIncludeReportName = !reportOptions.containsKey("coverIncludeReportName")
            || asBoolean(reportOptions.get("coverIncludeReportName"));
        String coverDatePattern = asString(reportOptions.get("coverDatePattern"));
        String coverAlignment = normalizeHorizontalAlignment(asString(reportOptions.get("coverAlignment")));
        String coverTheme = asString(reportOptions.get("coverTheme"));
        if (coverTheme.isBlank()) {
            coverTheme = "classicBlue";
        }
        boolean coverAccentEnabled = !reportOptions.containsKey("coverAccentEnabled")
            || asBoolean(reportOptions.get("coverAccentEnabled"));
        boolean coverBackgroundShapeEnabled = !reportOptions.containsKey("coverBackgroundShapeEnabled")
            || asBoolean(reportOptions.get("coverBackgroundShapeEnabled"));
        int coverTitleSize = asInt(reportOptions.get("coverTitleSize"), 30, 12, 72);
        int coverSubtitleSize = asInt(reportOptions.get("coverSubtitleSize"), 16, 10, 48);
        String coverLogoData = asString(reportOptions.get("coverLogoData"));
        String coverPageFileData = asString(reportOptions.get("coverPageFileData"));

        Map<String, List<Map<String, Object>>> coverBands = new HashMap<>();
        injectCoverPageElements(
            coverBands,
            reportName,
            coverTitle,
            coverSubtitle,
            coverAuthor,
            coverIncludeReportName,
            coverDateEnabled,
            coverDatePattern,
            coverAlignment,
            coverTheme,
            coverAccentEnabled,
            coverBackgroundShapeEnabled,
            coverTitleSize,
            coverSubtitleSize,
            coverLogoData,
            coverPageFileData,
            595,
            20,
            802,
            true);

        List<Map<String, Object>> titleElements = coverBands.get("title");
        if (titleElements == null || titleElements.isEmpty()) {
            return jrxmlContent;
        }

        int maxBottom = titleElements.stream()
            .mapToInt(element -> ((Number) element.getOrDefault("y", 0)).intValue()
                + Math.max(1, ((Number) element.getOrDefault("height", 20)).intValue()))
            .max()
            .orElse(555);
        int titleBandHeight = Math.max(595, maxBottom + 20);

        StringBuilder titleXml = new StringBuilder();
        titleXml.append("\t<title>\n");
        titleXml.append(String.format("\t\t<band height=\"%d\">\n", titleBandHeight));
        for (Map<String, Object> element : titleElements) {
            titleXml.append(generateElementXml(element, "title"));
        }
        titleXml.append("\t\t</band>\n");
        titleXml.append("\t</title>\n");

        return replaceTitleBand(jrxmlContent, titleXml.toString());
    }

    public String generateJrxmlFromVisualDesign(Map<String, Object> designData) {
        StringBuilder jrxml = new StringBuilder();

        @SuppressWarnings("unchecked")
        Map<String, Object> pageSettings = (Map<String, Object>) designData.getOrDefault("pageSettings", new HashMap<>());
        int pageWidth = ((Number) pageSettings.getOrDefault("width", 595)).intValue();
        int pageHeight = ((Number) pageSettings.getOrDefault("height", 842)).intValue();
        int leftMargin = ((Number) pageSettings.getOrDefault("leftMargin", 20)).intValue();
        int rightMargin = ((Number) pageSettings.getOrDefault("rightMargin", 20)).intValue();
        int topMargin = ((Number) pageSettings.getOrDefault("topMargin", 20)).intValue();
        int bottomMargin = ((Number) pageSettings.getOrDefault("bottomMargin", 20)).intValue();
        int printableWidth = Math.max(1, pageWidth - leftMargin - rightMargin);
        String orientation = String.valueOf(pageSettings.getOrDefault("orientation", pageWidth > pageHeight ? "Landscape" : "Portrait"));

        String reportName = (String) designData.getOrDefault("reportName", "VisualReport");
        String sqlQuery = (String) designData.get("sqlQuery");

        jrxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        jrxml.append("<!DOCTYPE jasperReport PUBLIC \"-//JasperReports//DTD Report Design//EN\" \"http://jasperreports.sourceforge.net/dtds/jasperreport.dtd\">\n");
        jrxml.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n");
        jrxml.append("              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        jrxml.append("              xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"\n");
        jrxml.append(String.format("              name=\"%s\"\n", reportName));
        jrxml.append(String.format("              pageWidth=\"%d\"\n", pageWidth));
        jrxml.append(String.format("              pageHeight=\"%d\"\n", pageHeight));
        jrxml.append(String.format("              orientation=\"%s\"\n", orientation));
        jrxml.append(String.format("              leftMargin=\"%d\"\n", leftMargin));
        jrxml.append(String.format("              rightMargin=\"%d\"\n", rightMargin));
        jrxml.append(String.format("              topMargin=\"%d\"\n", topMargin));
        jrxml.append(String.format("              bottomMargin=\"%d\"\n", bottomMargin));
        jrxml.append("              whenNoDataType=\"AllSectionsNoDetail\">\n\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) designData.get("fields");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) designData.getOrDefault("elements", new ArrayList<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> reportOptions = (Map<String, Object>) designData.getOrDefault("reportOptions", new HashMap<>());

        boolean headerFirstPageOnly = asBoolean(reportOptions.get("headerFirstPageOnly"));
        boolean coverPageEnabled = asBoolean(reportOptions.get("coverPageEnabled"));
        String coverTitle = asString(reportOptions.get("coverTitle"));
        String coverSubtitle = asString(reportOptions.get("coverSubtitle"));
        String coverAuthor = asString(reportOptions.get("coverAuthor"));
        boolean coverDateEnabled = asBoolean(reportOptions.get("coverDateEnabled"));
        boolean coverIncludeReportName = !reportOptions.containsKey("coverIncludeReportName")
            || asBoolean(reportOptions.get("coverIncludeReportName"));
        String coverDatePattern = asString(reportOptions.get("coverDatePattern"));
        String coverAlignment = normalizeHorizontalAlignment(asString(reportOptions.get("coverAlignment")));
        String coverTheme = asString(reportOptions.get("coverTheme"));
        if (coverTheme.isBlank()) {
            coverTheme = "classicBlue";
        }
        boolean coverAccentEnabled = !reportOptions.containsKey("coverAccentEnabled")
            || asBoolean(reportOptions.get("coverAccentEnabled"));
        boolean coverBackgroundShapeEnabled = !reportOptions.containsKey("coverBackgroundShapeEnabled")
            || asBoolean(reportOptions.get("coverBackgroundShapeEnabled"));
        int coverTitleSize = asInt(reportOptions.get("coverTitleSize"), 30, 12, 72);
        int coverSubtitleSize = asInt(reportOptions.get("coverSubtitleSize"), 16, 10, 48);
        String coverLogoData = asString(reportOptions.get("coverLogoData"));
        String coverPageFileData = asString(reportOptions.get("coverPageFileData"));

        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            sqlQuery = inferSqlQueryFromElements(elements);
        }
        if (fields == null || fields.isEmpty()) {
            fields = inferFieldsFromElements(elements);
        }

        if (sqlQuery != null && !sqlQuery.trim().isEmpty()) {
            jrxml.append("    <queryString>\n");
            jrxml.append(String.format("        <![CDATA[%s]]>\n", sqlQuery));
            jrxml.append("    </queryString>\n\n");
        }

        Set<String> fieldNames = new java.util.HashSet<>();
        for (Map<String, Object> element : elements) {
            if ("field".equals(element.get("type"))) {
                String fieldName = (String) element.get("fieldName");
                if (fieldName != null && !fieldName.isEmpty()) {
                    fieldNames.add(fieldName);
                }
            }
            if ("dbTable".equals(element.get("type"))) {
                for (String columnName : getSelectedColumns(element)) {
                    fieldNames.add(columnName);
                }
            }
        }

        if (fields != null && !fields.isEmpty()) {
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String fieldType = (String) field.get("type");
                if (fieldName != null && fieldNames.contains(fieldName)) {
                    jrxml.append(String.format("    <field name=\"%s\" class=\"%s\"/>\n",
                        fieldName,
                        resolveFieldClass(fieldType)));
                }
            }
        } else {
            for (String fieldName : fieldNames) {
                Map<String, Object> fieldElement = elements.stream()
                    .filter(e -> "field".equals(e.get("type")) && fieldName.equals(e.get("fieldName")))
                    .findFirst()
                    .orElse(null);

                String fieldType = "String";
                if (fieldElement != null && fieldElement.get("fieldType") != null) {
                    fieldType = (String) fieldElement.get("fieldType");
                }

                jrxml.append(String.format("    <field name=\"%s\" class=\"java.lang.%s\"/>\n",
                    fieldName, fieldType));
            }
        }

        jrxml.append("\n");

        Map<String, List<Map<String, Object>>> bandElements = new HashMap<>();
        for (Map<String, Object> element : elements) {
            String band = (String) element.getOrDefault("band", "detail");
            if ("title".equals(band) || "pageHeader".equals(band)) {
                band = headerFirstPageOnly ? "title" : "pageHeader";
            }
            bandElements.computeIfAbsent(band, k -> new ArrayList<>()).add(element);
        }

        if (coverPageEnabled) {
            boolean hasDataOrCanvasContent = (sqlQuery != null && !sqlQuery.trim().isEmpty()) || !elements.isEmpty();
            injectCoverPageElements(
                    bandElements,
                    reportName,
                    coverTitle,
                    coverSubtitle,
                    coverAuthor,
                    coverIncludeReportName,
                    coverDateEnabled,
                    coverDatePattern,
                    coverAlignment,
                    coverTheme,
                    coverAccentEnabled,
                    coverBackgroundShapeEnabled,
                    coverTitleSize,
                    coverSubtitleSize,
                    coverLogoData,
                    coverPageFileData,
                    pageHeight,
                    topMargin,
                    printableWidth,
                    hasDataOrCanvasContent);
        }

        List<Map<String, Object>> detailDbTables = elements.stream()
            .filter(element -> "dbTable".equals(element.get("type")))
            .map(HashMap::new)
            .collect(Collectors.toList());

        if (!detailDbTables.isEmpty()) {
            for (List<Map<String, Object>> bandList : bandElements.values()) {
                bandList.removeIf(element -> "dbTable".equals(element.get("type")));
            }

            List<Map<String, Object>> rawDetailElements = new ArrayList<>(bandElements.getOrDefault("detail", new ArrayList<>()));
            List<Map<String, Object>> detailRowElements = new ArrayList<>();
            List<Map<String, Object>> detailStaticElements = new ArrayList<>();
            List<Map<String, Object>> detailFooterElements = new ArrayList<>();

            detailRowElements.addAll(detailDbTables);

            for (Map<String, Object> element : rawDetailElements) {
                String type = String.valueOf(element.get("type"));

                if ("field".equals(type)) {
                    detailRowElements.add(element);
                } else if ("pageNumber".equals(type)) {
                    detailFooterElements.add(element);
                } else {
                    detailStaticElements.add(element);
                }
            }

            bandElements.put("detail", detailRowElements);

            if (!detailStaticElements.isEmpty()) {
                String staticHeaderBand = headerFirstPageOnly ? "title" : "pageHeader";
                bandElements.computeIfAbsent(staticHeaderBand, key -> new ArrayList<>()).addAll(detailStaticElements);
            }
            if (!detailFooterElements.isEmpty()) {
                bandElements.computeIfAbsent("pageFooter", key -> new ArrayList<>()).addAll(detailFooterElements);
            }
        }

        String[] bands = {"title", "pageHeader", "columnHeader", "detail", "columnFooter", "pageFooter", "summary"};

        for (String band : bands) {
            List<Map<String, Object>> bandElems = new ArrayList<>();
            if (bandElements.containsKey(band)) {
                bandElems.addAll(bandElements.get(band));
            }
            if ("pageHeader".equals(band) && !detailDbTables.isEmpty()) {
                bandElems.addAll(detailDbTables);
            }

            if (!bandElems.isEmpty()) {
                int dbTableHeaderYOffset = 0;
                if ("pageHeader".equals(band) && !detailDbTables.isEmpty()) {
                    int staticHeaderBottom = 0;
                    for (Map<String, Object> elem : bandElems) {
                        if ("dbTable".equals(elem.get("type"))) {
                            continue;
                        }
                        int elemY = ((Number) elem.getOrDefault("y", 0)).intValue();
                        int elemHeight = getEffectiveElementHeight(elem, band);
                        staticHeaderBottom = Math.max(staticHeaderBottom, elemY + elemHeight);
                    }
                    dbTableHeaderYOffset = staticHeaderBottom > 0 ? staticHeaderBottom + 8 : 0;
                }

                int minY = Integer.MAX_VALUE;
                int maxBottom = 0;
                for (Map<String, Object> elem : bandElems) {
                    int y = ((Number) elem.getOrDefault("y", 0)).intValue();
                    if ("dbTable".equals(elem.get("type")) && "pageHeader".equals(band)) {
                        y = dbTableHeaderYOffset;
                    } else if ("dbTable".equals(elem.get("type")) && "detail".equals(band)) {
                        y = 0;
                    } else if (!detailDbTables.isEmpty() && "detail".equals(band) && "field".equals(elem.get("type"))) {
                        y = 0;
                    }

                    int effectiveHeight = getEffectiveElementHeight(elem, band);
                    minY = Math.min(minY, y);
                    maxBottom = Math.max(maxBottom, y + effectiveHeight);
                }

                if (minY == Integer.MAX_VALUE) {
                    minY = 0;
                }
                int bandHeight = Math.max((maxBottom - minY) + 8, 24);

                jrxml.append(String.format("    <%s>\n", band));
                jrxml.append(String.format("        <band height=\"%d\">\n", bandHeight));

                for (Map<String, Object> elem : bandElems) {
                    Map<String, Object> relativeElem = new HashMap<>(elem);
                    int originalY = ((Number) elem.getOrDefault("y", 0)).intValue();

                    if ("dbTable".equals(elem.get("type")) && "pageHeader".equals(band)) {
                        relativeElem.put("y", Math.max(0, dbTableHeaderYOffset - minY));
                    } else if ("dbTable".equals(elem.get("type")) && "detail".equals(band)) {
                        relativeElem.put("y", 0);
                    } else if (!detailDbTables.isEmpty() && "detail".equals(band) && "field".equals(elem.get("type"))) {
                        relativeElem.put("y", 0);
                    } else {
                        relativeElem.put("y", Math.max(0, originalY - minY));
                    }

                    clampElementToPrintableWidth(relativeElem, printableWidth);

                    jrxml.append(generateElementXml(relativeElem, band));
                }

                jrxml.append("        </band>\n");
                jrxml.append(String.format("    </%s>\n", band));
            }
        }

        jrxml.append("</jasperReport>\n");

        return jrxml.toString();
    }

    public String resolveCoverImageMimeType(String contentTypeLower, String filenameLower) {
        if (contentTypeLower != null && contentTypeLower.startsWith("image/")) {
            return contentTypeLower;
        }
        if (filenameLower.endsWith(".png")) return "image/png";
        if (filenameLower.endsWith(".jpg") || filenameLower.endsWith(".jpeg")) return "image/jpeg";
        if (filenameLower.endsWith(".webp")) return "image/webp";
        if (filenameLower.endsWith(".gif")) return "image/gif";
        if (filenameLower.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private String replaceTitleBand(String jrxmlContent, String titleBandXml) {
        int titleStart = jrxmlContent.indexOf("\t<title>");
        if (titleStart >= 0) {
            int titleEnd = jrxmlContent.indexOf("\t</title>", titleStart);
            if (titleEnd >= 0) {
                int replaceEnd = titleEnd + "\t</title>".length();
                if (replaceEnd < jrxmlContent.length() && jrxmlContent.charAt(replaceEnd) == '\r') {
                    replaceEnd++;
                }
                if (replaceEnd < jrxmlContent.length() && jrxmlContent.charAt(replaceEnd) == '\n') {
                    replaceEnd++;
                }
                return jrxmlContent.substring(0, titleStart) + titleBandXml + jrxmlContent.substring(replaceEnd);
            }
        }

        int insertIndex = jrxmlContent.indexOf("\t<columnHeader>");
        if (insertIndex < 0) {
            insertIndex = jrxmlContent.indexOf("\t<detail>");
        }
        if (insertIndex < 0) {
            return jrxmlContent;
        }

        return jrxmlContent.substring(0, insertIndex) + titleBandXml + jrxmlContent.substring(insertIndex);
    }

    private String mapSqlTypeToJavaClass(String sqlType) {
        if (sqlType == null) return "java.lang.String";

        String upperType = sqlType.toUpperCase();
        if (upperType.contains("INT")) {
            return "java.lang.Integer";
        } else if (upperType.contains("LONG") || upperType.contains("BIGINT")) {
            return "java.lang.Long";
        } else if (upperType.contains("DECIMAL") || upperType.contains("NUMERIC")) {
            return "java.math.BigDecimal";
        } else if (upperType.contains("DOUBLE") || upperType.contains("FLOAT")) {
            return "java.lang.Double";
        } else if (upperType.contains("DATE")) {
            return "java.sql.Date";
        } else if (upperType.contains("TIME")) {
            return "java.sql.Timestamp";
        } else if (upperType.contains("BOOL")) {
            return "java.lang.Boolean";
        } else {
            return "java.lang.String";
        }
    }

    private String resolveFieldClass(String fieldType) {
        if (fieldType == null || fieldType.isBlank()) {
            return "java.lang.String";
        }
        if (fieldType.startsWith("java.")) {
            return fieldType;
        }
        return mapSqlTypeToJavaClass(fieldType);
    }

    @SuppressWarnings("unchecked")
    private String inferSqlQueryFromElements(List<Map<String, Object>> elements) {
        if (elements == null) {
            return null;
        }

        for (Map<String, Object> element : elements) {
            if (!"dbTable".equals(element.get("type"))) {
                continue;
            }

            String tableName = (String) element.get("tableName");
            List<String> selectedColumns = getSelectedColumns(element);
            if (tableName == null || tableName.isBlank() || selectedColumns.isEmpty()) {
                continue;
            }

            String columns = selectedColumns.stream()
                    .map(column -> "`" + column + "`")
                    .collect(Collectors.joining(", "));
            return "SELECT " + columns + " FROM `" + tableName + "`";
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> inferFieldsFromElements(List<Map<String, Object>> elements) {
        Map<String, Map<String, Object>> inferred = new LinkedHashMap<>();
        if (elements == null) {
            return new ArrayList<>();
        }

        for (Map<String, Object> element : elements) {
            if (!"dbTable".equals(element.get("type"))) {
                continue;
            }

            List<String> selectedColumns = getSelectedColumns(element);
            List<Map<String, Object>> columnDetails = (List<Map<String, Object>>) element.get("columnDetails");

            for (String columnName : selectedColumns) {
                String javaClass = "java.lang.String";
                if (columnDetails != null) {
                    for (Map<String, Object> detail : columnDetails) {
                        if (columnName.equals(String.valueOf(detail.get("name")))) {
                            Object explicitJavaClass = detail.get("javaClass");
                            Object fallbackType = detail.get("type");
                            if (explicitJavaClass != null && !String.valueOf(explicitJavaClass).isBlank()) {
                                javaClass = String.valueOf(explicitJavaClass);
                            } else if (fallbackType != null) {
                                javaClass = mapSqlTypeToJavaClass(String.valueOf(fallbackType));
                            }
                            break;
                        }
                    }
                }

                Map<String, Object> field = new HashMap<>();
                field.put("name", columnName);
                field.put("type", javaClass);
                inferred.put(columnName, field);
            }
        }

        return new ArrayList<>(inferred.values());
    }

    @SuppressWarnings("unchecked")
    private List<String> getSelectedColumns(Map<String, Object> element) {
        Object selectedColumnsObj = element.get("selectedColumns");
        if (selectedColumnsObj instanceof List<?> selectedColumns && !selectedColumns.isEmpty()) {
            return selectedColumns.stream().map(String::valueOf).collect(Collectors.toList());
        }

        Object columnsObj = element.get("columns");
        if (columnsObj instanceof List<?> columns) {
            return columns.stream().map(String::valueOf).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number numberValue) {
            parsed = numberValue.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static final class CoverThemePalette {
        private final String titleColor;
        private final String subtitleColor;
        private final String metaColor;
        private final String accentColor;
        private final String backgroundColor;

        private CoverThemePalette(String titleColor, String subtitleColor, String metaColor, String accentColor, String backgroundColor) {
            this.titleColor = titleColor;
            this.subtitleColor = subtitleColor;
            this.metaColor = metaColor;
            this.accentColor = accentColor;
            this.backgroundColor = backgroundColor;
        }
    }

    private String normalizeHorizontalAlignment(String value) {
        if (value == null) {
            return "Center";
        }
        String normalized = value.trim().toLowerCase();
        if ("left".equals(normalized)) {
            return "Left";
        }
        if ("right".equals(normalized)) {
            return "Right";
        }
        return "Center";
    }

    private CoverThemePalette resolveCoverThemePalette(String coverTheme) {
        String normalized = coverTheme == null ? "" : coverTheme.trim().toLowerCase();
        switch (normalized) {
            case "forest":
                return new CoverThemePalette("#1F4A38", "#2E6A52", "#4C7564", "#2F8F6D", "#EAF7F1");
            case "sunrise":
                return new CoverThemePalette("#6A3A1B", "#9A5728", "#A16C47", "#E07B39", "#FFF2E8");
            case "charcoal":
                return new CoverThemePalette("#2A2F36", "#3E4650", "#59626D", "#8C99A8", "#F2F5F8");
            case "midnightgold":
                return new CoverThemePalette("#2A2A3D", "#4A4762", "#666375", "#B08A2E", "#F6F1E3");
            case "classicblue":
            default:
                return new CoverThemePalette("#143A62", "#2F5C8A", "#486A8E", "#2E75B6", "#EAF2FB");
        }
    }

    private void injectCoverPageElements(
            Map<String, List<Map<String, Object>>> bandElements,
            String reportName,
            String coverTitle,
            String coverSubtitle,
            String coverAuthor,
            boolean coverIncludeReportName,
            boolean coverDateEnabled,
            String coverDatePattern,
            String coverAlignment,
            String coverTheme,
            boolean coverAccentEnabled,
            boolean coverBackgroundShapeEnabled,
            int coverTitleSize,
            int coverSubtitleSize,
            String coverLogoData,
            String coverPageFileData,
            int pageHeight,
            int topMargin,
            int printableWidth,
            boolean appendPageBreak) {

        String effectiveTitle = coverTitle;
        if ((effectiveTitle == null || effectiveTitle.isBlank()) && coverIncludeReportName) {
            effectiveTitle = reportName;
        }
        if (effectiveTitle != null && effectiveTitle.toLowerCase().endsWith(".jrxml")) {
            effectiveTitle = effectiveTitle.substring(0, effectiveTitle.length() - 6);
        }

        List<Map<String, Object>> titleBand = bandElements.computeIfAbsent("title", key -> new ArrayList<>());
        CoverThemePalette palette = resolveCoverThemePalette(coverTheme);

        int safePrintableWidth = Math.max(140, printableWidth);
        int titleY = Math.max(44, (pageHeight / 3) - topMargin);
        int subtitleY = titleY + Math.max(42, coverTitleSize + 12);
        int metaY = subtitleY + Math.max(36, coverSubtitleSize + 16);

        if (coverPageFileData != null && !coverPageFileData.isBlank()) {
            int coverImageHeight = Math.max(240, pageHeight - (topMargin * 2));
            titleBand.add(createCoverBackgroundImageElement(0, 0, safePrintableWidth, coverImageHeight, coverPageFileData));
        }

        if (coverBackgroundShapeEnabled) {
            int shapeHeight = Math.max(180, Math.min(280, pageHeight / 2));
            titleBand.add(createCoverBackgroundShape(0, 0, safePrintableWidth, shapeHeight, palette.backgroundColor));
        }

        if (coverLogoData != null && !coverLogoData.isBlank()) {
            int logoWidth = Math.max(120, Math.min(260, safePrintableWidth / 3));
            int logoX;
            if ("Left".equals(coverAlignment)) {
                logoX = 0;
            } else if ("Right".equals(coverAlignment)) {
                logoX = Math.max(0, safePrintableWidth - logoWidth);
            } else {
                logoX = (safePrintableWidth - logoWidth) / 2;
            }
            int logoY = Math.max(24, titleY - 112);
            titleBand.add(createCoverLogoElement(logoX, logoY, logoWidth, 80, coverLogoData));
        }

        if (effectiveTitle != null && !effectiveTitle.isBlank()) {
            titleBand.add(createCoverTextElement(0, titleY, safePrintableWidth, Math.max(36, coverTitleSize + 10), effectiveTitle, coverTitleSize, true, coverAlignment, palette.titleColor));
        }
        if (coverSubtitle != null && !coverSubtitle.isBlank()) {
            titleBand.add(createCoverTextElement(0, subtitleY, safePrintableWidth, Math.max(24, coverSubtitleSize + 8), coverSubtitle, coverSubtitleSize, false, coverAlignment, palette.subtitleColor));
        }

        if (coverAuthor != null && !coverAuthor.isBlank()) {
            titleBand.add(createCoverTextElement(0, metaY, safePrintableWidth, 22, coverAuthor, 12, false, coverAlignment, palette.metaColor));
            metaY += 28;
        }

        if (coverDateEnabled) {
            String effectivePattern = (coverDatePattern == null || coverDatePattern.isBlank()) ? "dd/MM/yyyy" : coverDatePattern;
            titleBand.add(createCoverDateElement(0, metaY, safePrintableWidth, 22, effectivePattern, coverAlignment, palette.metaColor));
            metaY += 28;
        }

        if (coverAccentEnabled) {
            titleBand.add(createCoverAccentLine(0, metaY + 2, safePrintableWidth, palette.accentColor));
        }

        titleBand.removeIf(element -> "pageBreak".equals(element.get("type")));

        int coverBottom = titleBand.stream()
                .filter(element -> !"pageBreak".equals(element.get("type")))
                .mapToInt(element -> ((Number) element.getOrDefault("y", 0)).intValue()
                        + ((Number) element.getOrDefault("height", 20)).intValue())
                .max()
                .orElse(titleY + 120);

        if (appendPageBreak) {
            titleBand.add(createPageBreakElement(Math.max(coverBottom + 20, 120), safePrintableWidth));
        }
    }

    private Map<String, Object> createCoverTextElement(
            int x,
            int y,
            int width,
            int height,
            String text,
            int fontSize,
            boolean bold,
            String alignment,
            String color) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "staticText");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("text", text == null ? "" : text);
        element.put("fontName", "DejaVu Sans");
        element.put("fontSize", fontSize);
        element.put("bold", bold);
        element.put("italic", false);
        element.put("alignment", alignment);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createPageBreakElement(int y, int width) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "pageBreak");
        element.put("x", 0);
        element.put("y", Math.max(0, y));
        element.put("width", Math.max(1, width));
        element.put("height", 1);
        return element;
    }

    private Map<String, Object> createCoverDateElement(
            int x,
            int y,
            int width,
            int height,
            String pattern,
            String alignment,
            String color) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "date");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("pattern", pattern);
        element.put("fontName", "DejaVu Sans");
        element.put("fontSize", 12);
        element.put("alignment", alignment);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createCoverBackgroundShape(
            int x,
            int y,
            int width,
            int height,
            String backgroundColor) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "rectangle");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("backgroundTransparent", false);
        element.put("backgroundColor", backgroundColor);
        element.put("borderColor", backgroundColor);
        element.put("borderWidth", 0);
        return element;
    }

    private Map<String, Object> createCoverBackgroundImageElement(
            int x,
            int y,
            int width,
            int height,
            String imageData) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "image");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("imageData", imageData);
        return element;
    }

    private Map<String, Object> createCoverAccentLine(int x, int y, int width, String color) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "line");
        element.put("x", x);
        element.put("y", Math.max(0, y));
        element.put("width", Math.max(1, width));
        element.put("height", 2);
        element.put("borderWidth", 1);
        element.put("color", color);
        return element;
    }

    private Map<String, Object> createCoverLogoElement(
            int x,
            int y,
            int width,
            int height,
            String imageData) {

        Map<String, Object> element = new HashMap<>();
        element.put("type", "image");
        element.put("x", x);
        element.put("y", y);
        element.put("width", width);
        element.put("height", height);
        element.put("imageData", imageData);
        return element;
    }

    private int getEffectiveElementHeight(Map<String, Object> element, String band) {
        String type = (String) element.get("type");
        int configuredHeight = ((Number) element.getOrDefault("height", 20)).intValue();

        if ("pageBreak".equals(type)) {
            return Math.max(1, configuredHeight);
        }

        if ("dbTable".equals(type)) {
            if ("pageHeader".equals(band) || "columnHeader".equals(band)) {
                return getDbTableHeaderHeight(configuredHeight);
            }
            if ("detail".equals(band)) {
                return getDbTableRowHeight(configuredHeight);
            }
            return getDbTableHeaderHeight(configuredHeight) + getDbTableRowHeight(configuredHeight);
        }

        return configuredHeight;
    }

    private int getDbTableHeaderHeight(int configuredHeight) {
        return Math.min(24, Math.max(18, configuredHeight / 6));
    }

    private int getDbTableRowHeight(int configuredHeight) {
        return Math.min(24, Math.max(18, configuredHeight / 6));
    }

    private void clampElementToPrintableWidth(Map<String, Object> element, int printableWidth) {
        int x = ((Number) element.getOrDefault("x", 0)).intValue();
        int width = ((Number) element.getOrDefault("width", 100)).intValue();

        x = Math.max(0, x);
        width = Math.max(1, width);

        if (x >= printableWidth) {
            x = 0;
        }
        if (x + width > printableWidth) {
            width = Math.max(1, printableWidth - x);
        }

        element.put("x", x);
        element.put("width", width);
    }

    private String normalizeHexColor(String color, String fallback) {
        String effectiveFallback = (fallback == null || fallback.isBlank()) ? "#000000" : fallback;
        if (color == null || color.isBlank()) {
            return effectiveFallback;
        }

        String normalized = color.trim();
        if (normalized.matches("#[0-9a-fA-F]{6}")) {
            return normalized.toUpperCase();
        }
        if (normalized.matches("#[0-9a-fA-F]{3}")) {
            char r = normalized.charAt(1);
            char g = normalized.charAt(2);
            char b = normalized.charAt(3);
            return ("#" + r + r + g + g + b + b).toUpperCase();
        }
        return effectiveFallback;
    }

    private String generateElementXml(Map<String, Object> element, String band) {
        StringBuilder xml = new StringBuilder();
        String type = (String) element.get("type");
        int x = ((Number) element.getOrDefault("x", 0)).intValue();
        int y = ((Number) element.getOrDefault("y", 0)).intValue();
        int width = ((Number) element.getOrDefault("width", 100)).intValue();
        int height = ((Number) element.getOrDefault("height", 20)).intValue();

        switch (type) {
            case "text":
            case "label":
            case "staticText":
                String text = (String) element.getOrDefault("text", "");
                String fontName = (String) element.getOrDefault("fontName", "Arial");
                int fontSize = ((Number) element.getOrDefault("fontSize", 12)).intValue();
                boolean isBold = (boolean) element.getOrDefault("bold", false);
                boolean isItalic = (boolean) element.getOrDefault("italic", false);
                String alignment = (String) element.getOrDefault("alignment", "Left");
                String color = (String) element.getOrDefault("color", "#000000");
                String textColor = normalizeHexColor(color, "#000000");

                xml.append("            <staticText>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, textColor));
                xml.append("                <textElement");
                if (!alignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", alignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"", fontName, fontSize));
                if (isBold) xml.append(" isBold=\"true\"");
                if (isItalic) xml.append(" isItalic=\"true\"");
                xml.append("/>\n");
                xml.append("                </textElement>\n");
                xml.append(String.format("                <text><![CDATA[%s]]></text>\n", text));
                xml.append("            </staticText>\n");
                break;

            case "field":
                String fieldName = (String) element.getOrDefault("fieldName", "fieldName");
                String fieldFontName = (String) element.getOrDefault("fontName", "Arial");
                int fieldFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                boolean fieldBold = (boolean) element.getOrDefault("bold", false);
                boolean fieldItalic = (boolean) element.getOrDefault("italic", false);
                String fieldAlignment = (String) element.getOrDefault("alignment", "Left");
                String fieldColor = (String) element.getOrDefault("color", "#000000");
                String safeFieldColor = normalizeHexColor(fieldColor, "#000000");
                String pattern = (String) element.getOrDefault("pattern", "");

                xml.append("            <textField");
                if (pattern != null && !pattern.isEmpty()) {
                    xml.append(String.format(" pattern=\"%s\"", pattern));
                }
                xml.append(">\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, safeFieldColor));
                xml.append("                <textElement");
                if (!fieldAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", fieldAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"", fieldFontName, fieldFontSize));
                if (fieldBold) xml.append(" isBold=\"true\"");
                if (fieldItalic) xml.append(" isItalic=\"true\"");
                xml.append("/>\n");
                xml.append("                </textElement>\n");
                xml.append(String.format("                <textFieldExpression><![CDATA[$F{%s}]]></textFieldExpression>\n", fieldName));
                xml.append("            </textField>\n");
                break;

            case "pageNumber":
                String pageText = (String) element.getOrDefault("text", "Page ");
                String pageFontName = (String) element.getOrDefault("fontName", "Arial");
                int pageFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                String pageAlignment = (String) element.getOrDefault("alignment", "Right");
                String pageColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");

                xml.append("            <textField>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, pageColor));
                xml.append("                <textElement");
                if (!pageAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", pageAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"/>\n", pageFontName, pageFontSize));
                xml.append("                </textElement>\n");
                xml.append(String.format("                <textFieldExpression><![CDATA[\"%s\" + $V{PAGE_NUMBER}]]></textFieldExpression>\n", pageText));
                xml.append("            </textField>\n");
                break;

            case "date":
            case "currentDate":
                String datePattern = (String) element.getOrDefault("pattern", "dd/MM/yyyy");
                String dateFontName = (String) element.getOrDefault("fontName", "Arial");
                int dateFontSize = ((Number) element.getOrDefault("fontSize", 10)).intValue();
                String dateAlignment = (String) element.getOrDefault("alignment", "Left");
                String dateColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");

                xml.append(String.format("            <textField pattern=\"%s\">\n", datePattern));
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" forecolor=\"%s\"/>\n", x, y, width, height, dateColor));
                xml.append("                <textElement");
                if (!dateAlignment.equals("Left")) {
                    xml.append(String.format(" textAlignment=\"%s\"", dateAlignment));
                }
                xml.append(">\n");
                xml.append(String.format("                    <font fontName=\"%s\" size=\"%d\"/>\n", dateFontName, dateFontSize));
                xml.append("                </textElement>\n");
                xml.append("                <textFieldExpression><![CDATA[new java.util.Date()]]></textFieldExpression>\n");
                xml.append("            </textField>\n");
                break;

            case "logo":
            case "image":
                String imageData = (String) element.getOrDefault("imageData", element.getOrDefault("imagePath", ""));
                if (imageData != null && !imageData.isBlank()) {
                    String base64 = imageData.contains(",") ? imageData.substring(imageData.indexOf(',') + 1) : imageData;
                    xml.append("            <image scaleImage=\"RetainShape\">\n");
                    xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                    xml.append(String.format("                <imageExpression><![CDATA[new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(\"%s\"))]]></imageExpression>\n", base64));
                    xml.append("            </image>\n");
                }
                break;

            case "dbTable":
                if ("pageHeader".equals(band) || "columnHeader".equals(band)) {
                    xml.append(generateDbTableHeaderXml(element, x, y, width, height));
                } else {
                    xml.append(generateDbTableDetailXml(element, x, y, width, height));
                }
                break;

            case "line":
                String lineColor = normalizeHexColor((String) element.getOrDefault("color", "#000000"), "#000000");
                int lineWidth = Math.max(1, ((Number) element.getOrDefault("borderWidth", 1)).intValue());
                xml.append("            <line>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", x, y, width, height));
                xml.append("                <graphicElement>\n");
                xml.append(String.format("                    <pen lineWidth=\"%d\" lineColor=\"%s\"/>\n", lineWidth, lineColor));
                xml.append("                </graphicElement>\n");
                xml.append("            </line>\n");
                break;

            case "rectangle":
                String rectangleBorderColor = normalizeHexColor((String) element.getOrDefault("borderColor", "#000000"), "#000000");
                String rectangleBackgroundColor = normalizeHexColor((String) element.getOrDefault("backgroundColor", "#FFFFFF"), "#FFFFFF");
                int rectangleBorderWidth = Math.max(0, ((Number) element.getOrDefault("borderWidth", 1)).intValue());
                boolean rectangleTransparent = !element.containsKey("backgroundTransparent")
                        || asBoolean(element.get("backgroundTransparent"));

                xml.append("            <rectangle>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"", x, y, width, height));
                if (!rectangleTransparent) {
                    xml.append(String.format(" mode=\"Opaque\" backcolor=\"%s\"", rectangleBackgroundColor));
                }
                xml.append("/>\n");
                xml.append("                <graphicElement>\n");
                xml.append(String.format("                    <pen lineWidth=\"%d\" lineColor=\"%s\"/>\n", rectangleBorderWidth, rectangleBorderColor));
                xml.append("                </graphicElement>\n");
                xml.append("            </rectangle>\n");
                break;

            case "pageBreak":
                xml.append("            <break>\n");
                xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"1\"/>\n", x, y, Math.max(1, width)));
                xml.append("            </break>\n");
                break;
        }

        return xml.toString();
    }

    private String generateDbTableHeaderXml(Map<String, Object> element, int x, int y, int width, int height) {
        StringBuilder xml = new StringBuilder();
        List<String> selectedColumns = getSelectedColumns(element);
        if (selectedColumns.isEmpty()) {
            return "";
        }

        int columnCount = Math.max(1, selectedColumns.size());
        int columnWidth = Math.max(60, width / columnCount);
        int headerHeight = getDbTableHeaderHeight(height);

        for (int i = 0; i < selectedColumns.size(); i++) {
            String column = selectedColumns.get(i);
            int columnX = x + (i * columnWidth);
            int effectiveWidth = (i == selectedColumns.size() - 1) ? Math.max(60, width - (i * columnWidth)) : columnWidth;

            xml.append("            <staticText>\n");
            xml.append(String.format("                <reportElement mode=\"Opaque\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" backcolor=\"#DDE6F5\"/>\n", columnX, y, effectiveWidth, headerHeight));
            xml.append("                <box><pen lineWidth=\"1.0\"/></box>\n");
            xml.append("                <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
            xml.append("                    <font size=\"10\" isBold=\"true\"/>\n");
            xml.append("                </textElement>\n");
            xml.append(String.format("                <text><![CDATA[%s]]></text>\n", column));
            xml.append("            </staticText>\n");
        }

        return xml.toString();
    }

    private String generateDbTableDetailXml(Map<String, Object> element, int x, int y, int width, int height) {
        StringBuilder xml = new StringBuilder();
        List<String> selectedColumns = getSelectedColumns(element);
        if (selectedColumns.isEmpty()) {
            return "";
        }

        int columnCount = Math.max(1, selectedColumns.size());
        int columnWidth = Math.max(60, width / columnCount);
        int rowHeight = getDbTableRowHeight(height);

        for (int i = 0; i < selectedColumns.size(); i++) {
            String column = selectedColumns.get(i);
            int columnX = x + (i * columnWidth);
            int effectiveWidth = (i == selectedColumns.size() - 1) ? Math.max(60, width - (i * columnWidth)) : columnWidth;

            xml.append("            <textField isStretchWithOverflow=\"true\">\n");
            xml.append(String.format("                <reportElement x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n", columnX, y, effectiveWidth, rowHeight));
            xml.append("                <box><pen lineWidth=\"1.0\"/></box>\n");
            xml.append("                <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n");
            xml.append("                    <font size=\"10\"/>\n");
            xml.append("                </textElement>\n");
            xml.append(String.format("                <textFieldExpression><![CDATA[$F{%s}]]></textFieldExpression>\n", column));
            xml.append("            </textField>\n");
        }

        return xml.toString();
    }
}
