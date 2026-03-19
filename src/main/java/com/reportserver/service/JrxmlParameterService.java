package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Service
public class JrxmlParameterService {

    private static final Logger logger = LoggerFactory.getLogger(JrxmlParameterService.class);

    public Map<String, Object> validateAndConvertReportParameters(String jrxmlPath, Map<String, String> requestParameters) {
        Map<String, Object> converted = new HashMap<>();
        if (requestParameters == null || requestParameters.isEmpty()) {
            return converted;
        }

        Map<String, String> expectedTypes = readExpectedParameterTypes(jrxmlPath);
        for (Map.Entry<String, String> entry : requestParameters.entrySet()) {
            String key = entry.getKey();
            if (isReservedGenerateParameterName(key)) {
                continue;
            }

            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            String expectedType = expectedTypes.get(key);
            if (expectedType == null) {
                converted.put(key, rawValue);
                continue;
            }

            converted.put(key, convertParameterValue(key, rawValue, expectedType));
        }
        return converted;
    }

    private boolean isReservedGenerateParameterName(String key) {
        return "reportName".equals(key)
                || "format".equals(key)
                || "useDatabase".equals(key)
                || "datasourceId".equals(key)
                || "category".equals(key)
                || "tags".equals(key)
                || "_csrf".equals(key);
    }

    private Map<String, String> readExpectedParameterTypes(String jrxmlPath) {
        Map<String, String> expectedTypes = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(jrxmlPath));
            NodeList parameterNodes = document.getElementsByTagName("parameter");

            for (int i = 0; i < parameterNodes.getLength(); i++) {
                Element paramElement = (Element) parameterNodes.item(i);
                String name = paramElement.getAttribute("name");
                String className = paramElement.getAttribute("class");
                if (!name.startsWith("REPORT_") && !name.equals("JASPER_REPORT")) {
                    expectedTypes.put(name, className);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse JRXML parameter definitions for validation: {}", e.getMessage());
        }
        return expectedTypes;
    }

    private Object convertParameterValue(String parameterName, String rawValue, String javaType) {
        try {
            if (javaType == null || javaType.isBlank() || javaType.contains("String")) {
                return rawValue;
            }
            if (javaType.contains("Boolean")) {
                if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                    throw new IllegalArgumentException("must be true or false");
                }
                return Boolean.parseBoolean(rawValue);
            }
            if (javaType.contains("Integer")) {
                return Integer.parseInt(rawValue);
            }
            if (javaType.contains("Long")) {
                return Long.parseLong(rawValue);
            }
            if (javaType.contains("Double")) {
                return Double.parseDouble(rawValue);
            }
            if (javaType.contains("Float")) {
                return Float.parseFloat(rawValue);
            }
            if (javaType.contains("BigDecimal")) {
                return new BigDecimal(rawValue);
            }
            if (javaType.contains("BigInteger")) {
                return new BigInteger(rawValue);
            }
            if (javaType.contains("Timestamp")) {
                return Timestamp.from(Instant.parse(rawValue + "T00:00:00Z"));
            }
            if (javaType.contains("Date")) {
                return Date.valueOf(rawValue);
            }
            return rawValue;
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Parameter '" + parameterName + "' expects " + javaType + " but got value '" + rawValue + "'");
        }
    }
}
