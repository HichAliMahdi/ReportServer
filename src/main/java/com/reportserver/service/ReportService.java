package com.reportserver.service;

import com.reportserver.config.CacheConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private JRDataSourceProviderService jrDataSourceProviderService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    // ─── Cache: compiled JasperReport objects ────────────────────────────────

    /**
     * Compile a JRXML file to a JasperReport, caching the result by file path.
     * Cache key uses jrxmlPath (absolute). The cache TTL (30 min) ensures
     * stale compiled objects are eventually replaced after JRXML edits.
     */
    public JasperReport compileReport(String jrxmlPath) throws JRException {
        File jrxmlFile = new File(jrxmlPath);
        long lastModified = jrxmlFile.exists() ? jrxmlFile.lastModified() : -1L;
        String cacheKey = jrxmlPath + "::" + lastModified;

        Cache cache = cacheManager.getCache(CacheConfig.COMPILED_REPORTS_CACHE);
        if (cache != null) {
            JasperReport cached = cache.get(cacheKey, JasperReport.class);
            if (cached != null) {
                return cached;
            }
        }

        logger.debug("Compiling JRXML (cache miss): {}", jrxmlPath);
        JasperReport compiled = JasperCompileManager.compileReport(jrxmlPath);
        if (cache != null) {
            cache.put(cacheKey, compiled);
        }
        return compiled;
    }

    /**
     * Evict the compiled report cache entry after a JRXML file is saved/replaced.
     */
    public void evictCompiledReport(String jrxmlPath) {
        Cache cache = cacheManager.getCache(CacheConfig.COMPILED_REPORTS_CACHE);
        if (cache != null) {
            Set<String> keysToEvict = new LinkedHashSet<>();
            if (jrxmlPath != null && !jrxmlPath.isBlank()) {
                keysToEvict.add(jrxmlPath);

                File file = new File(jrxmlPath);
                keysToEvict.add(file.getPath());
                keysToEvict.add(file.getAbsolutePath());
                try {
                    keysToEvict.add(file.getCanonicalPath());
                } catch (IOException ignored) {
                    // Ignore canonical resolution failures and evict known forms.
                }
            }

            for (String key : keysToEvict) {
                cache.evict(key);
            }
        }
        logger.debug("Evicted compiled report cache variants for: {}", jrxmlPath);
    }

    /**
     * Clear all compiled reports from cache after bulk template changes.
     */
    public void clearCompiledReportCache() {
        Cache cache = cacheManager.getCache(CacheConfig.COMPILED_REPORTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
        logger.debug("Cleared compiled report cache");
    }

    // ─── Public generation API ────────────────────────────────────────────────

    public byte[] generateReport(String jrxmlPath, Map<String, Object> parameters,
                                  String outputFormat, Connection connection) throws Exception {
        return generateReportInternal(jrxmlPath, parameters, outputFormat, connection, null);
    }

    public byte[] generateReportWithDataSource(String jrxmlPath, Map<String, Object> parameters,
                                                String outputFormat, Object dataSource) throws Exception {
        return generateReportInternal(jrxmlPath, parameters, outputFormat, null, dataSource);
    }

    public byte[] generateReportFromJrxmlContent(String jrxmlContent, Map<String, Object> parameters,
                                                  String outputFormat, Connection connection) throws Exception {
        return generateReportFromJrxmlContentInternal(jrxmlContent, parameters, outputFormat, connection, null);
    }

    public byte[] generateReportFromJrxmlContentWithDataSource(String jrxmlContent, Map<String, Object> parameters,
                                                                String outputFormat, Object dataSource) throws Exception {
        return generateReportFromJrxmlContentInternal(jrxmlContent, parameters, outputFormat, null, dataSource);
    }

    private byte[] generateReportInternal(String jrxmlPath, Map<String, Object> parameters,
                                           String outputFormat, Connection connection,
                                           Object dataSource) throws Exception {

        File jrxmlFile = new File(jrxmlPath);
        if (!jrxmlFile.exists()) {
            throw new FileNotFoundException("Report file not found: " + jrxmlPath);
        }

        Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        boolean success = false;
        try {
            // Use cached compilation
            JasperReport compiledReport = compileReport(jrxmlPath);

            // Fill report with data
            JasperPrint filledReport;
            if (connection != null) {
                filledReport = JasperFillManager.fillReport(compiledReport, parameters, connection);
            } else if (dataSource != null) {
                if (dataSource instanceof JRDataSource) {
                    filledReport = JasperFillManager.fillReport(compiledReport, parameters, (JRDataSource) dataSource);
                } else if (dataSource instanceof Connection) {
                    filledReport = JasperFillManager.fillReport(compiledReport, parameters, (Connection) dataSource);
                } else {
                    filledReport = JasperFillManager.fillReport(compiledReport, parameters, new JREmptyDataSource());
                }
            } else {
                filledReport = JasperFillManager.fillReport(compiledReport, parameters, new JREmptyDataSource());
            }

            byte[] result = export(filledReport, outputFormat);
            success = true;
            return result;
        } finally {
            if (sample != null && meterRegistry != null) {
                sample.stop(Timer.builder("reportserver.reports.generation.duration")
                        .tag("format", outputFormat)
                        .tag("status", success ? "success" : "failure")
                        .description("Time to generate a report")
                        .register(meterRegistry));
            }
            if (meterRegistry != null) {
                Counter.builder("reportserver.reports.generated.total")
                        .tag("format", outputFormat)
                        .tag("status", success ? "success" : "failure")
                        .description("Total reports generated")
                        .register(meterRegistry)
                        .increment();
            }
        }
    }

    private byte[] generateReportFromJrxmlContentInternal(String jrxmlContent, Map<String, Object> parameters,
                                                           String outputFormat, Connection connection,
                                                           Object dataSource) throws Exception {
        if (jrxmlContent == null || jrxmlContent.isBlank()) {
            throw new IllegalArgumentException("JRXML content is empty");
        }

        Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        boolean success = false;
        try (InputStream inputStream = new ByteArrayInputStream(jrxmlContent.getBytes(StandardCharsets.UTF_8))) {
            JasperReport compiledReport = JasperCompileManager.compileReport(inputStream);

            Map<String, Object> safeParameters = parameters != null ? parameters : new HashMap<>();
            JasperPrint filledReport;
            if (connection != null) {
                filledReport = JasperFillManager.fillReport(compiledReport, safeParameters, connection);
            } else if (dataSource != null) {
                if (dataSource instanceof JRDataSource) {
                    filledReport = JasperFillManager.fillReport(compiledReport, safeParameters, (JRDataSource) dataSource);
                } else if (dataSource instanceof Connection) {
                    filledReport = JasperFillManager.fillReport(compiledReport, safeParameters, (Connection) dataSource);
                } else {
                    filledReport = JasperFillManager.fillReport(compiledReport, safeParameters, new JREmptyDataSource());
                }
            } else {
                filledReport = JasperFillManager.fillReport(compiledReport, safeParameters, new JREmptyDataSource());
            }

            byte[] result = export(filledReport, outputFormat);
            success = true;
            return result;
        } finally {
            if (sample != null && meterRegistry != null) {
                sample.stop(Timer.builder("reportserver.reports.generation.duration")
                        .tag("format", outputFormat)
                        .tag("status", success ? "success" : "failure")
                        .description("Time to generate a report")
                        .register(meterRegistry));
            }
            if (meterRegistry != null) {
                Counter.builder("reportserver.reports.generated.total")
                        .tag("format", outputFormat)
                        .tag("status", success ? "success" : "failure")
                        .description("Total reports generated")
                        .register(meterRegistry)
                        .increment();
            }
        }
    }

    // ─── Export engine ────────────────────────────────────────────────────────

    private byte[] export(JasperPrint filledReport, String outputFormat) throws Exception {
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
