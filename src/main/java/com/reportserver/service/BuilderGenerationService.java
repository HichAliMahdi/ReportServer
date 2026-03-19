package com.reportserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.dto.BuilderGenerateRequestDTO;
import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import com.reportserver.model.SharedReport;
import com.reportserver.repository.SharedReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BuilderGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(BuilderGenerationService.class);
    private static final String DEFAULT_OUTPUT_FORMAT = "pdf";
    private static final String GENERATED_REPORTS_DIR = "data/generated-reports/";

    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DataSourceService dataSourceService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final JrxmlBuilderService jrxmlBuilderService;
    private final ReportService reportService;
    private final SharedReportRepository sharedReportRepository;

    public BuilderGenerationService(
            DataSourceService dataSourceService,
            SchemaIntrospectionService schemaIntrospectionService,
            JrxmlBuilderService jrxmlBuilderService,
            ReportService reportService,
            SharedReportRepository sharedReportRepository) {
        this.dataSourceService = dataSourceService;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.jrxmlBuilderService = jrxmlBuilderService;
        this.reportService = reportService;
        this.sharedReportRepository = sharedReportRepository;
    }

    public ResponseEntity<?> generateReport(BuilderGenerateRequestDTO request) {
        try {
            FormBuildResult build = buildJrxmlFromFormRequest(request);
            saveJrxmlTemplate(build.reportName, build.jrxmlContent);

            logger.info("Successfully generated JRXML file: {}", build.reportName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "JRXML template generated successfully",
                    "reportName", build.reportName
            ));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid form builder generation request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating JRXML", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> generateAndReport(BuilderGenerateRequestDTO request) {
        try {
            FormBuildResult build = buildJrxmlFromFormRequest(request);
            saveJrxmlTemplate(build.reportName, build.jrxmlContent);
            String outputFormat = resolveOutputFormat(request.getReportFormat());

            Map<String, Object> generated = generateAndPersistReport(
                    build.reportName,
                    build.jrxmlContent,
                    build.datasourceId,
                    outputFormat,
                    resolveCurrentUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "JRXML template and report generated successfully",
                    "reportName", build.reportName,
                    "reportId", generated.get("reportId"),
                    "fileName", generated.get("fileName"),
                        "reportFormat", outputFormat
            ));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid form builder generate-and-report request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating JRXML and report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> generateReportOnly(BuilderGenerateRequestDTO request) {
        try {
            FormBuildResult build = buildJrxmlFromFormRequest(request);
            String outputFormat = resolveOutputFormat(request.getReportFormat());
            Map<String, Object> generated = generateAndPersistReport(
                    build.reportName,
                    build.jrxmlContent,
                    build.datasourceId,
                    outputFormat,
                    resolveCurrentUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Report generated successfully (JRXML template not saved)",
                    "reportName", build.reportName,
                    "reportId", generated.get("reportId"),
                    "fileName", generated.get("fileName"),
                        "reportFormat", outputFormat
            ));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid form builder report-only request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating report from form builder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    public ResponseEntity<Map<String, Object>> generateFromVisualDesign(Map<String, Object> designData) {
        Map<String, Object> response = new HashMap<>();

        try {
            VisualBuildResult build = buildJrxmlFromVisualDesign(designData);
            saveJrxmlTemplate(build.reportName, build.jrxmlContent);

            response.put("success", true);
            response.put("message", "Report generated successfully from visual design");
            response.put("reportName", build.reportName);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid visual builder generation request: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error generating JRXML from visual design", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> generateFromVisualDesignAndReport(Map<String, Object> designData) {
        Map<String, Object> response = new HashMap<>();

        try {
            VisualBuildResult build = buildJrxmlFromVisualDesign(designData);
            saveJrxmlTemplate(build.reportName, build.jrxmlContent);
            String outputFormat = resolveOutputFormat(asString(designData.get("reportFormat")));

            Map<String, Object> generated = generateAndPersistReport(
                    build.reportName,
                    build.jrxmlContent,
                    build.datasourceId,
                    outputFormat,
                    resolveCurrentUsername());

            response.put("success", true);
            response.put("message", "JRXML template and report generated successfully from visual design");
            response.put("reportName", build.reportName);
            response.put("reportId", generated.get("reportId"));
            response.put("fileName", generated.get("fileName"));
            response.put("reportFormat", outputFormat);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid visual builder generate-and-report request: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error generating visual builder template and report", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> generateFromVisualDesignReportOnly(Map<String, Object> designData) {
        Map<String, Object> response = new HashMap<>();

        try {
            VisualBuildResult build = buildJrxmlFromVisualDesign(designData);
            String outputFormat = resolveOutputFormat(asString(designData.get("reportFormat")));
            Map<String, Object> generated = generateAndPersistReport(
                    build.reportName,
                    build.jrxmlContent,
                    build.datasourceId,
                    outputFormat,
                    resolveCurrentUsername());

            response.put("success", true);
            response.put("message", "Report generated successfully from visual design (JRXML template not saved)");
            response.put("reportName", build.reportName);
            response.put("reportId", generated.get("reportId"));
            response.put("fileName", generated.get("fileName"));
            response.put("reportFormat", outputFormat);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid visual builder report-only request: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error generating visual builder report only", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private FormBuildResult buildJrxmlFromFormRequest(BuilderGenerateRequestDTO request) throws Exception {
        String reportName = normalizeReportName(request.getReportName());
        String tableName = request.getTableName();
        List<String> columns = request.getColumns();
        Long datasourceId = request.getDatasourceId();

        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column must be selected");
        }
        if (datasourceId == null) {
            throw new IllegalArgumentException("Datasource ID is required");
        }

        logger.info("Generating JRXML for table {} with {} columns", tableName, columns.size());

        try (Connection connection = dataSourceService.getConnection(datasourceId)) {
            if (connection == null) {
                throw new IllegalArgumentException("Failed to connect to datasource");
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

            List<ParameterDTO> parameters = parseParameters(request.getParametersJson());
            List<VariableDTO> variables = parseVariables(request.getVariablesJson());
            Map<String, Object> reportOptions = parseReportOptions(request.getReportOptionsJson());

            String jrxmlContent = jrxmlBuilderService.generateJrxml(
                    reportName.replace(".jrxml", ""),
                    tableName,
                    selectedColumns,
                    parameters,
                    variables
            );

            jrxmlContent = jrxmlBuilderService.applySharedCoverToBuilderJrxml(jrxmlContent, reportName, reportOptions);
            return new FormBuildResult(reportName, jrxmlContent, datasourceId);
        }
    }

    private VisualBuildResult buildJrxmlFromVisualDesign(Map<String, Object> designData) {
        String reportName = normalizeReportName((String) designData.get("reportName"));
        String jrxmlContent = jrxmlBuilderService.generateJrxmlFromVisualDesign(designData);

        Long datasourceId = null;
        Object datasourceIdRaw = designData.get("datasourceId");
        if (datasourceIdRaw instanceof Number numberValue) {
            datasourceId = numberValue.longValue();
        } else if (datasourceIdRaw != null && !String.valueOf(datasourceIdRaw).isBlank()) {
            try {
                datasourceId = Long.parseLong(String.valueOf(datasourceIdRaw));
            } catch (NumberFormatException ignored) {
                datasourceId = null;
            }
        }

        return new VisualBuildResult(reportName, jrxmlContent, datasourceId);
    }

    private File saveJrxmlTemplate(String reportName, String jrxmlContent) throws Exception {
        File targetUploadDir = new File(this.uploadDir);
        if (!targetUploadDir.exists()) {
            targetUploadDir.mkdirs();
        }

        File jrxmlFile = new File(this.uploadDir + reportName);
        try (FileWriter writer = new FileWriter(jrxmlFile)) {
            writer.write(jrxmlContent);
        }

        reportService.evictCompiledReport(this.uploadDir + reportName);
        reportService.evictCompiledReport(jrxmlFile.getAbsolutePath());
        return jrxmlFile;
    }

    private Map<String, Object> generateAndPersistReport(
            String reportName,
            String jrxmlContent,
            Long datasourceId,
            String outputFormat,
            String username) throws Exception {

        byte[] reportBytes;
        Map<String, Object> runtimeParams = new HashMap<>();
        if (datasourceId != null) {
            try (Connection connection = dataSourceService.getConnection(datasourceId)) {
                if (connection == null) {
                    throw new IllegalArgumentException("Failed to connect to datasource");
                }
                reportBytes = reportService.generateReportFromJrxmlContent(jrxmlContent, runtimeParams, outputFormat, connection);
            }
        } else {
            reportBytes = reportService.generateReportFromJrxmlContent(jrxmlContent, runtimeParams, outputFormat, null);
        }

        String extension = getFileExtension(outputFormat);
        String reportBaseName = reportName.replace(".jrxml", "");
        String generatedFileName = reportBaseName + "_" + System.currentTimeMillis() + "." + extension;

        File generatedDir = new File(GENERATED_REPORTS_DIR);
        if (!generatedDir.exists()) {
            generatedDir.mkdirs();
        }

        Path generatedPath = Paths.get(GENERATED_REPORTS_DIR + generatedFileName);
        Files.write(generatedPath, reportBytes);

        SharedReport sharedReport = new SharedReport();
        sharedReport.setReportFileName(generatedFileName);
        sharedReport.setReportName(reportBaseName);
        sharedReport.setReportFormat(outputFormat);
        sharedReport.setCreatedBy(username);
        sharedReport.setSharedWithReadOnly(false);

        SharedReport saved = sharedReportRepository.save(sharedReport);

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", saved.getId());
        result.put("fileName", generatedFileName);
        result.put("reportName", reportBaseName);
        return result;
    }

    private String normalizeReportName(String rawReportName) {
        if (rawReportName == null || rawReportName.trim().isEmpty()) {
            throw new IllegalArgumentException("Report name is required");
        }

        String reportName = rawReportName.trim();
        if (!reportName.endsWith(".jrxml")) {
            reportName += ".jrxml";
        }
        return reportName;
    }

    private List<ParameterDTO> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<ParameterDTO> parameters = objectMapper.readValue(parametersJson, new TypeReference<List<ParameterDTO>>() {});
            logger.info("Parsed {} parameter(s) from JSON", parameters.size());
            return parameters;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid parameters format: " + e.getMessage());
        }
    }

    private List<VariableDTO> parseVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<VariableDTO> variables = objectMapper.readValue(variablesJson, new TypeReference<List<VariableDTO>>() {});
            logger.info("Parsed {} variable(s) from JSON", variables.size());
            return variables;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid variables format: " + e.getMessage());
        }
    }

    private Map<String, Object> parseReportOptions(String reportOptionsJson) {
        if (reportOptionsJson == null || reportOptionsJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(reportOptionsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid report options format: " + e.getMessage());
        }
    }

    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }

    private String getFileExtension(String format) {
        String normalized = format == null ? DEFAULT_OUTPUT_FORMAT : format.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "html" -> "html";
            case "xlsx" -> "xlsx";
            case "xls" -> "xls";
            case "docx" -> "docx";
            case "rtf" -> "rtf";
            case "odt" -> "odt";
            case "csv" -> "csv";
            case "xml" -> "xml";
            case "txt", "text" -> "txt";
            default -> "pdf";
        };
    }

    private String resolveOutputFormat(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            return DEFAULT_OUTPUT_FORMAT;
        }

        String normalized = requestedFormat.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pdf", "html", "xlsx", "xls", "docx", "rtf", "odt", "csv", "xml", "txt", "text" ->
                    "text".equals(normalized) ? "txt" : normalized;
            default -> throw new IllegalArgumentException("Unsupported report format: " + requestedFormat);
        };
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class FormBuildResult {
        private final String reportName;
        private final String jrxmlContent;
        private final Long datasourceId;

        private FormBuildResult(String reportName, String jrxmlContent, Long datasourceId) {
            this.reportName = reportName;
            this.jrxmlContent = jrxmlContent;
            this.datasourceId = datasourceId;
        }
    }

    private static final class VisualBuildResult {
        private final String reportName;
        private final String jrxmlContent;
        private final Long datasourceId;

        private VisualBuildResult(String reportName, String jrxmlContent, Long datasourceId) {
            this.reportName = reportName;
            this.jrxmlContent = jrxmlContent;
            this.datasourceId = datasourceId;
        }
    }
}
