package com.reportserver.service;

import net.sf.jasperreports.engine.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class ThumbnailGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailGenerationService.class);

    @Value("${report.upload.directory:./data/reports}")
    private String uploadDir;

    @Value("${report.thumbnail.directory:./data/thumbnails}")
    private String thumbnailDir;

    @Value("${report.thumbnail.page:0}")
    private int thumbnailPage; // 0-based index of page to use for thumbnail

    @Value("${report.thumbnail.scale:0.25}")
    private float thumbnailScale; // 0.25 = 25% of original size

    /**
     * Generate a thumbnail PNG from the first page of a JRXML report
     * @param jrxmlPath - path to compiled .jasper file
     * @param reportName - name for thumbnail file
     * @param parameters - report parameters (can be null)
     * @return path to generated thumbnail file
     */
    public String generateThumbnail(String jrxmlPath, String reportName, Map<String, Object> parameters) {
        try {
            ensureThumbnailDirExists();

            // Load compiled report
            JasperReport jasperReport = (JasperReport) JRLoader.loadObjectFromFile(jrxmlPath);

            // Fill report with parameters and empty data source for thumbnail generation
            if (parameters == null) {
                parameters = new HashMap<>();
            }
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                jasperReport,
                parameters,
                new JREmptyDataSource()
            );

            // Extract the first page as BufferedImage
            if (jasperPrint.getPages().isEmpty()) {
                logger.warn("Report {} has no pages, skipping thumbnail generation", reportName);
                return null;
            }

            BufferedImage pageImage = generatedPageImage(jasperPrint, thumbnailPage);
            if (pageImage == null) {
                logger.warn("Failed to generate image for page {} of report {}", thumbnailPage, reportName);
                return null;
            }

            // Scale down the image
            BufferedImage scaledImage = scaleImage(pageImage, thumbnailScale);

            // Save as PNG
            String thumbnailFileName = reportName.replace(".jrxml", "") + "-thumbnail.png";
            String thumbnailPath = Paths.get(thumbnailDir, thumbnailFileName).toString();

            File thumbnailFile = new File(thumbnailPath);
            ImageIO.write(scaledImage, "png", thumbnailFile);

            logger.info("Generated thumbnail for report {} at {}", reportName, thumbnailPath);

            // Return relative path for storage in DB
            return "/thumbnails/" + thumbnailFileName;

        } catch (Exception e) {
            logger.error("Failed to generate thumbnail for report {}: {}", reportName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate thumbnail from a filled JasperPrint (for scheduled reports)
     */
    public String generateThumbnailFromPrint(JasperPrint jasperPrint, String reportName) {
        try {
            ensureThumbnailDirExists();

            if (jasperPrint.getPages().isEmpty()) {
                logger.warn("JasperPrint {} has no pages, skipping thumbnail", reportName);
                return null;
            }

            BufferedImage pageImage = generatedPageImage(jasperPrint, thumbnailPage);
            if (pageImage == null) {
                logger.warn("Failed to generate image for page {} of print {}", thumbnailPage, reportName);
                return null;
            }

            BufferedImage scaledImage = scaleImage(pageImage, thumbnailScale);

            String thumbnailFileName = reportName.replace(".jrxml", "") + "-thumbnail.png";
            String thumbnailPath = Paths.get(thumbnailDir, thumbnailFileName).toString();

            File thumbnailFile = new File(thumbnailPath);
            ImageIO.write(scaledImage, "png", thumbnailFile);

            logger.info("Generated thumbnail from JasperPrint {} at {}", reportName, thumbnailPath);
            return "/thumbnails/" + thumbnailFileName;

        } catch (Exception e) {
            logger.error("Failed to generate thumbnail from print {}: {}", reportName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract a specific page from JasperPrint as BufferedImage
     */
    private BufferedImage generatedPageImage(JasperPrint jasperPrint, int pageIndex) throws JRException {
        try {
            JRGraphics2DExporter exporter = new JRGraphics2DExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));

            // Create a graphics context for rendering
            int pageWidth = jasperPrint.getPageWidth();
            int pageHeight = jasperPrint.getPageHeight();

            BufferedImage bufferedImage = new BufferedImage(
                pageWidth,
                pageHeight,
                BufferedImage.TYPE_INT_RGB
            );

            Graphics2D graphics = bufferedImage.createGraphics();
            // Fill with white background
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, pageWidth, pageHeight);

            // Set exporter output to graphics
            SimpleGraphics2DExporterOutput output = new SimpleGraphics2DExporterOutput();
            output.setGraphics2D(graphics);
            exporter.setExporterOutput(output);

            // Export specific page
            SimpleExporterConfiguration config = new SimpleExporterConfiguration();
            config.setPageIndex(pageIndex);
            exporter.setConfiguration(config);

            exporter.exportReport();
            graphics.dispose();

            return bufferedImage;

        } catch (Exception e) {
            logger.error("Failed to generate image from page {}: {}", pageIndex, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Scale image to percentage of original size
     */
    private BufferedImage scaleImage(BufferedImage originalImage, float scale) {
        int newWidth = Math.round(originalImage.getWidth() * scale);
        int newHeight = Math.round(originalImage.getHeight() * scale);

        BufferedImage scaledImage = new BufferedImage(
            newWidth,
            newHeight,
            BufferedImage.TYPE_INT_RGB
        );

        java.awt.Graphics2D graphics = scaledImage.createGraphics();
        graphics.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_HIGH_QUALITY
        );
        graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return scaledImage;
    }

    /**
     * Delete thumbnail for a report
     */
    public void deleteThumbnail(String thumbnailPath) {
        if (thumbnailPath == null || thumbnailPath.isEmpty()) {
            return;
        }

        try {
            String fullPath = Paths.get(thumbnailDir, thumbnailPath.replace("/thumbnails/", "")).toString();
            Files.deleteIfExists(Paths.get(fullPath));
            logger.info("Deleted thumbnail at {}", fullPath);
        } catch (IOException e) {
            logger.warn("Failed to delete thumbnail at {}: {}", thumbnailPath, e.getMessage());
        }
    }

    private void ensureThumbnailDirExists() throws IOException {
        Path dir = Paths.get(thumbnailDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
