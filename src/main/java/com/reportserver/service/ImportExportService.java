package com.reportserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.DataSource;
import com.reportserver.model.ReportTemplate;
import com.reportserver.repository.DataSourceRepository;
import com.reportserver.repository.ReportTemplateRepository;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ImportExportService {
    private static final Logger logger = LoggerFactory.getLogger(ImportExportService.class);

    private final ReportTemplateRepository reportTemplateRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ObjectMapper objectMapper;

    @Value("${report.upload.directory:./data/reports}")
    private String uploadDir;

    @Value("${report.export.directory:./data/exports}")
    private String exportDir;

    public ImportExportService(
            ReportTemplateRepository reportTemplateRepository,
            DataSourceRepository dataSourceRepository,
            ObjectMapper objectMapper) {
        this.reportTemplateRepository = reportTemplateRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a report bundle as ZIP containing JRXML + datasource config + metadata
     */
    public File exportReportBundle(String reportFileName) {
        try {
            ensureExportDirExists();

            Optional<ReportTemplate> template = reportTemplateRepository.findByReportFileName(reportFileName);
            if (template.isEmpty()) {
                throw new RuntimeException("Report template not found: " + reportFileName);
            }

            ReportTemplate reportTemplate = template.get();

            // Create temporary ZIP file
            String zipFileName = reportTemplate.getDisplayName()
                .replaceAll("[^a-zA-Z0-9.-]", "_") + "_bundle.zip";
            File zipFile = new File(Paths.get(exportDir, zipFileName).toString());

            try (ZipArchiveOutputStream zipOutput = new ZipArchiveOutputStream(
                    new FileOutputStream(zipFile))) {

                // Add JRXML file
                File jrxmlFile = new File(Paths.get(uploadDir, reportFileName).toString());
                if (jrxmlFile.exists()) {
                    addFileToZip(zipOutput, jrxmlFile, reportFileName);
                }

                // Add compiled .jasper file if exists
                String jasperFileName = reportFileName.replace(".jrxml", ".jasper");
                File jasperFile = new File(Paths.get(uploadDir, jasperFileName).toString());
                if (jasperFile.exists()) {
                    addFileToZip(zipOutput, jasperFile, jasperFileName);
                }

                // Add metadata JSON
                Map<String, Object> metadata = createBundleMetadata(reportTemplate);
                String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata);
                addStringToZip(zipOutput, metadataJson, "metadata.json");

                // Add datasource configs if available
                List<DataSource> datasources = dataSourceRepository.findAll();
                if (!datasources.isEmpty()) {
                    String dsJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(datasources);
                    addStringToZip(zipOutput, dsJson, "datasources.json");
                }

                zipOutput.finish();
            }

            logger.info("Exported report bundle {} to {}", reportTemplate.getDisplayName(), zipFile.getAbsolutePath());
            return zipFile;

        } catch (Exception e) {
            logger.error("Failed to export report bundle {}: {}", reportFileName, e.getMessage(), e);
            throw new RuntimeException("Export failed", e);
        }
    }

    /**
     * Import a report bundle from ZIP file
     */
    public ReportTemplate importReportBundle(File zipFile, String userId, String workspaceId) {
        try {
            ReportTemplate importedTemplate = null;
            Map<String, Object> bundleMetadata = new HashMap<>();
            Map<String, Object> datasourceConfigs = new HashMap<>();

            try (ZipArchiveInputStream zipInput = new ZipArchiveInputStream(
                    new FileInputStream(zipFile))) {

                ZipArchiveEntry entry;
                while ((entry = zipInput.getNextZipEntry()) != null) {
                    String entryName = entry.getName();

                    if (entryName.endsWith(".jrxml")) {
                        // Extract and compile JRXML
                        File jrxmlFile = extractZipEntry(zipInput, entryName, uploadDir);
                        logger.info("Imported JRXML: {}", entryName);

                        // Create ReportTemplate entity
                        importedTemplate = createTemplateFromBundle(
                            entryName,
                            bundleMetadata,
                            userId,
                            workspaceId
                        );

                    } else if (entryName.endsWith(".jasper")) {
                        // Extract compiled report
                        extractZipEntry(zipInput, entryName, uploadDir);
                        logger.info("Imported compiled report: {}", entryName);

                    } else if (entryName.equals("metadata.json")) {
                        // Parse metadata
                        String metadataJson = IOUtils.toString(zipInput, StandardCharsets.UTF_8);
                        bundleMetadata = objectMapper.readValue(metadataJson, Map.class);
                        logger.info("Imported metadata: {}", metadataJson);

                    } else if (entryName.equals("datasources.json")) {
                        // Parse datasource configs
                        String dsJson = IOUtils.toString(zipInput, StandardCharsets.UTF_8);
                        List<Map<String, Object>> dsList = objectMapper.readValue(
                            dsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                        );
                        logger.info("Imported {} datasources from bundle", dsList.size());
                        // Note: datasources are descriptive only; actual persistence depends on business requirements
                    }
                }
            }

            if (importedTemplate != null) {
                ReportTemplate saved = reportTemplateRepository.save(importedTemplate);
                logger.info("Successfully imported report bundle: {}", saved.getDisplayName());
                return saved;
            } else {
                throw new RuntimeException("No JRXML file found in bundle");
            }

        } catch (Exception e) {
            logger.error("Failed to import report bundle: {}", e.getMessage(), e);
            throw new RuntimeException("Import failed", e);
        }
    }

    /**
     * Create a ZIP file containing report definitions (used for bulk export)
     */
    public File exportMultipleReports(List<String> reportFileNames) {
        try {
            ensureExportDirExists();

            String zipFileName = "reports_export_" + System.currentTimeMillis() + ".zip";
            File zipFile = new File(Paths.get(exportDir, zipFileName).toString());

            try (ZipArchiveOutputStream zipOutput = new ZipArchiveOutputStream(
                    new FileOutputStream(zipFile))) {

                for (String reportFileName : reportFileNames) {
                    Optional<ReportTemplate> template = reportTemplateRepository.findByReportFileName(reportFileName);
                    if (template.isPresent()) {
                        // Add JRXML
                        File jrxmlFile = new File(Paths.get(uploadDir, reportFileName).toString());
                        if (jrxmlFile.exists()) {
                            addFileToZip(zipOutput, jrxmlFile, reportFileName);
                        }

                        // Add metadata JSON
                        String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(createBundleMetadata(template.get()));
                        String metadataFileName = reportFileName.replace(".jrxml", "_metadata.json");
                        addStringToZip(zipOutput, metadataJson, metadataFileName);
                    }
                }

                zipOutput.finish();
            }

            logger.info("Exported {} reports to bulk bundle: {}", reportFileNames.size(), zipFile.getAbsolutePath());
            return zipFile;

        } catch (Exception e) {
            logger.error("Failed to export multiple reports: {}", e.getMessage(), e);
            throw new RuntimeException("Bulk export failed", e);
        }
    }

    // Helper methods

    private Map<String, Object> createBundleMetadata(ReportTemplate template) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", template.getId());
        metadata.put("reportFileName", template.getReportFileName());
        metadata.put("displayName", template.getDisplayName());
        metadata.put("category", template.getCategory());
        metadata.put("tags", template.getTags());
        metadata.put("description", template.getDescription());
        metadata.put("createdAt", template.getCreatedAt());
        metadata.put("createdBy", template.getCreatedBy());
        return metadata;
    }

    private ReportTemplate createTemplateFromBundle(String reportFileName, Map<String, Object> metadata,
                                                      String userId, String workspaceId) {
        ReportTemplate template = new ReportTemplate();
        template.setReportFileName(reportFileName);
        template.setDisplayName((String) metadata.getOrDefault("displayName", reportFileName));
        template.setCategory((String) metadata.getOrDefault("category", "Imported"));
        template.setTags((String) metadata.getOrDefault("tags", ""));
        template.setDescription((String) metadata.getOrDefault("description", ""));
        template.setCreatedBy(userId);
        // Note: workspace assignment depends on implementation
        return template;
    }

    private File extractZipEntry(ZipArchiveInputStream zipInput, String entryName, String destDir)
            throws IOException {
        File destFile = new File(Paths.get(destDir, entryName).toString());
        destFile.getParentFile().mkdirs();

        try (FileOutputStream fileOutput = new FileOutputStream(destFile)) {
            IOUtils.copy(zipInput, fileOutput);
        }

        return destFile;
    }

    private void addFileToZip(ZipArchiveOutputStream zipOutput, File file, String entryName)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(file, entryName);
        zipOutput.putArchiveEntry(entry);

        try (FileInputStream fileInput = new FileInputStream(file)) {
            IOUtils.copy(fileInput, zipOutput);
        }

        zipOutput.closeArchiveEntry();
    }

    private void addStringToZip(ZipArchiveOutputStream zipOutput, String content, String entryName)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
        zipOutput.putArchiveEntry(entry);
        zipOutput.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutput.closeArchiveEntry();
    }

    private void ensureExportDirExists() throws IOException {
        Path dir = Paths.get(exportDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
