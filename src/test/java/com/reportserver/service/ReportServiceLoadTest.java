package com.reportserver.service;

import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lightweight load/perf tests to validate compiled-report caching and
 * concurrent report generation behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportServiceLoadTest {

    @Autowired
    private ReportService reportService;

    @TempDir
    Path tempDir;

    private Path reportFile;

    @BeforeEach
    void setUp() throws Exception {
        reportFile = tempDir.resolve("load_test_report.jrxml");
        Files.writeString(reportFile, jrxml());
    }

    @Test
    void compileReport_CacheHit_IsFasterThanFirstCompile() throws Exception {
        long t0 = System.nanoTime();
        JasperReport first = reportService.compileReport(reportFile.toString());
        long firstMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        assertNotNull(first);

        int iterations = 30;
        long cachedTotalMs = 0;
        for (int i = 0; i < iterations; i++) {
            long s = System.nanoTime();
            JasperReport cached = reportService.compileReport(reportFile.toString());
            cachedTotalMs += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s);
            assertNotNull(cached);
        }

        long avgCachedMs = cachedTotalMs / iterations;

        assertTrue(avgCachedMs <= firstMs,
                "Expected cached compile avg <= first compile. firstMs=" + firstMs + ", avgCachedMs=" + avgCachedMs);
    }

    @Test
    void generateReport_ConcurrentRequests_AllSucceedWithinTimeout() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<CompletableFuture<byte[]>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return reportService.generateReport(reportFile.toString(), new HashMap<>(), "pdf", null);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, pool));
            }

            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(30, TimeUnit.SECONDS);

            for (CompletableFuture<byte[]> f : futures) {
                byte[] data = f.get(1, TimeUnit.SECONDS);
                assertNotNull(data);
                assertTrue(data.length > 0);
                assertEquals("%PDF", new String(data, 0, 4));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private String jrxml() {
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"
                              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                              xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports
                              http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"
                              name=\"LoadTest\" pageWidth=\"595\" pageHeight=\"842\"
                              columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\"
                              topMargin=\"20\" bottomMargin=\"20\">
                    <detail>
                        <band height=\"20\">
                            <staticText>
                                <reportElement x=\"0\" y=\"0\" width=\"120\" height=\"20\"/>
                                <text><![CDATA[Load Test]]></text>
                            </staticText>
                        </band>
                    </detail>
                </jasperReport>
                """;
    }
}
