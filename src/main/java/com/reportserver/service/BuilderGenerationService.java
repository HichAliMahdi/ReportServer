package com.reportserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.dto.BuilderGenerateRequestDTO;
import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BuilderGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(BuilderGenerationService.class);

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    private final DataSourceService dataSourceService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final JrxmlBuilderService jrxmlBuilderService;
    private final ReportService reportService;

    public BuilderGenerationService(
            DataSourceService dataSourceService,
            SchemaIntrospectionService schemaIntrospectionService,
            JrxmlBuilderService jrxmlBuilderService,
            ReportService reportService) {
        this.dataSourceService = dataSourceService;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.jrxmlBuilderService = jrxmlBuilderService;
        this.reportService = reportService;
    }

    public ResponseEntity<?> generateReport(BuilderGenerateRequestDTO request) {
        String reportName = request.getReportName();
        String tableName = request.getTableName();
        List<String> columns = request.getColumns();
        Long datasourceId = request.getDatasourceId();
        String parametersJson = request.getParametersJson();
        String variablesJson = request.getVariablesJson();
        String reportOptionsJson = request.getReportOptionsJson();

        Connection connection = null;
        try {
            logger.info("Generating JRXML for table {} with {} columns", tableName, columns.size());

            if (reportName == null || reportName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Report name is required"));
            }

            if (!reportName.endsWith(".jrxml")) {
                reportName += ".jrxml";
            }

            if (columns == null || columns.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "At least one column must be selected"));
            }

            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<Map<String, String>> allColumns = schemaIntrospectionService.getColumns(connection, tableName);

            List<Map<String, String>> selectedColumns = new ArrayList<>();
            for (String columnName : columns) {
                for (Map<String, String> col : allColumns) {
                    if (col.get("name").equals(columnName)) {
                        selectedColumns.add(col);
                        break;
                    }
                }
            }

            List<ParameterDTO> parameters = new ArrayList<>();
            if (parametersJson != null && !parametersJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    parameters = objectMapper.readValue(parametersJson, new TypeReference<List<ParameterDTO>>() {});
                    logger.info("Parsed {} parameter(s) from JSON", parameters.size());
                } catch (Exception e) {
                    logger.error("Error parsing parameters JSON", e);
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Invalid parameters format: " + e.getMessage()));
                }
            }

            List<VariableDTO> variables = new ArrayList<>();
            if (variablesJson != null && !variablesJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    variables = objectMapper.readValue(variablesJson, new TypeReference<List<VariableDTO>>() {});
                    logger.info("Parsed {} variable(s) from JSON", variables.size());
                } catch (Exception e) {
                    logger.error("Error parsing variables JSON", e);
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Invalid variables format: " + e.getMessage()));
                }
            }

            Map<String, Object> reportOptions = new HashMap<>();
            if (reportOptionsJson != null && !reportOptionsJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    reportOptions = objectMapper.readValue(reportOptionsJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    logger.error("Error parsing reportOptions JSON", e);
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Invalid report options format: " + e.getMessage()));
                }
            }

            String jrxmlContent = jrxmlBuilderService.generateJrxml(
                    reportName.replace(".jrxml", ""),
                    tableName,
                    selectedColumns,
                    parameters,
                    variables
            );

            jrxmlContent = jrxmlBuilderService.applySharedCoverToBuilderJrxml(jrxmlContent, reportName, reportOptions);

            File uploadDir = new File(this.uploadDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(this.uploadDir + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            reportService.evictCompiledReport(this.uploadDir + reportName);
            reportService.evictCompiledReport(jrxmlFile.getAbsolutePath());

            logger.info("Successfully generated JRXML file: {}", reportName);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Report generated successfully",
                    "reportName", reportName
            ));

        } catch (Exception e) {
            logger.error("Error generating JRXML", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    public ResponseEntity<Map<String, Object>> generateFromVisualDesign(Map<String, Object> designData) {
        Map<String, Object> response = new HashMap<>();

        try {
            String reportName = (String) designData.get("reportName");
            if (reportName == null || reportName.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Report name is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (!reportName.endsWith(".jrxml")) {
                reportName += ".jrxml";
            }

            String jrxmlContent = jrxmlBuilderService.generateJrxmlFromVisualDesign(designData);

            File uploadDir = new File(this.uploadDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(this.uploadDir + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            reportService.evictCompiledReport(this.uploadDir + reportName);
            reportService.evictCompiledReport(jrxmlFile.getAbsolutePath());

            logger.info("Successfully generated JRXML from visual design: {}", reportName);

            response.put("success", true);
            response.put("message", "Report generated successfully from visual design");
            response.put("reportName", reportName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating JRXML from visual design", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
