package com.reportserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BuilderAssetService {

    private static final Logger logger = LoggerFactory.getLogger(BuilderAssetService.class);
    private static final String IMAGES_DIR = "data/images/";
    private static final String TEMPLATES_DIR = "data/templates/";

    private final JrxmlBuilderService jrxmlBuilderService;

    public BuilderAssetService(JrxmlBuilderService jrxmlBuilderService) {
        this.jrxmlBuilderService = jrxmlBuilderService;
    }

    public ResponseEntity<Map<String, Object>> uploadImage(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                response.put("success", false);
                response.put("message", "Only image files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            File imagesDir = new File(IMAGES_DIR);
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            String fileName = System.currentTimeMillis() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = Paths.get(IMAGES_DIR + fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Successfully uploaded image: {}", fileName);

            response.put("success", true);
            response.put("message", "Image uploaded successfully");
            response.put("fileName", fileName);
            response.put("filePath", IMAGES_DIR + fileName);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error uploading image", e);
            response.put("success", false);
            response.put("message", "Error uploading image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> uploadCoverFile(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            if (file.getSize() > (20L * 1024L * 1024L)) {
                response.put("success", false);
                response.put("message", "File is too large. Maximum allowed size is 20 MB");
                return ResponseEntity.badRequest().body(response);
            }

            String originalFilename = file.getOriginalFilename();
            String filenameLower = originalFilename == null ? "" : originalFilename.toLowerCase();
            String contentType = file.getContentType();
            String contentTypeLower = contentType == null ? "" : contentType.toLowerCase();

            boolean isPdf = "application/pdf".equals(contentTypeLower) || filenameLower.endsWith(".pdf");
            boolean isImage = contentTypeLower.startsWith("image/")
                    || filenameLower.matches(".*\\.(png|jpg|jpeg|webp|gif|bmp)$");

            if (!isPdf && !isImage) {
                response.put("success", false);
                response.put("message", "Unsupported file format. Supported: PDF, PNG, JPG, JPEG, WEBP, GIF, BMP");
                return ResponseEntity.badRequest().body(response);
            }

            String coverImageData;
            boolean convertedFromPdf = false;

            if (isPdf) {
                byte[] pdfBytes = file.getBytes();
                try (PDDocument document = PDDocument.load(pdfBytes);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                    if (document.getNumberOfPages() == 0) {
                        response.put("success", false);
                        response.put("message", "Uploaded PDF has no pages");
                        return ResponseEntity.badRequest().body(response);
                    }

                    PDFRenderer renderer = new PDFRenderer(document);
                    BufferedImage firstPage = renderer.renderImageWithDPI(0, 150);
                    ImageIO.write(firstPage, "png", out);

                    coverImageData = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
                    convertedFromPdf = true;
                }
            } else {
                String imageMimeType = jrxmlBuilderService.resolveCoverImageMimeType(contentTypeLower, filenameLower);
                coverImageData = "data:" + imageMimeType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
            }

            response.put("success", true);
            response.put("message", convertedFromPdf
                    ? "PDF uploaded and first page converted successfully"
                    : "Cover image uploaded successfully");
            response.put("coverImageData", coverImageData);
            response.put("convertedFromPdf", convertedFromPdf);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading cover file", e);
            response.put("success", false);
            response.put("message", "Error uploading cover file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> listImages() {
        Map<String, Object> response = new HashMap<>();

        try {
            File imagesDir = new File(IMAGES_DIR);
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            File[] files = imagesDir.listFiles((dir, name) ->
                    name.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp|svg)$"));

            List<Map<String, String>> imageList = new ArrayList<>();
            if (files != null) {
                for (File imageFile : files) {
                    Map<String, String> imageInfo = new HashMap<>();
                    imageInfo.put("name", imageFile.getName());
                    imageInfo.put("path", IMAGES_DIR + imageFile.getName());
                    imageInfo.put("size", String.valueOf(imageFile.length()));
                    imageList.add(imageInfo);
                }
            }

            response.put("success", true);
            response.put("images", imageList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error listing images", e);
            response.put("success", false);
            response.put("message", "Error listing images: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> deleteImage(String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            File file = new File(IMAGES_DIR + fileName);
            if (!file.exists()) {
                response.put("success", false);
                response.put("message", "Image not found");
                return ResponseEntity.notFound().build();
            }

            if (file.delete()) {
                logger.info("Successfully deleted image: {}", fileName);
                response.put("success", true);
                response.put("message", "Image deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete image");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            logger.error("Error deleting image: {}", fileName, e);
            response.put("success", false);
            response.put("message", "Error deleting image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> saveTemplate(Map<String, Object> templateData) {
        Map<String, Object> response = new HashMap<>();

        try {
            String templateName = (String) templateData.get("name");
            if (templateName == null || templateName.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Template name is required");
                return ResponseEntity.badRequest().body(response);
            }

            File templatesDir = new File(TEMPLATES_DIR);
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }

            String fileName = templateName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
            File templateFile = new File(TEMPLATES_DIR + fileName);

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(templateFile, templateData);

            logger.info("Successfully saved template: {}", fileName);

            response.put("success", true);
            response.put("message", "Template saved successfully");
            response.put("fileName", fileName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error saving template", e);
            response.put("success", false);
            response.put("message", "Error saving template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> listTemplates() {
        Map<String, Object> response = new HashMap<>();

        try {
            File templatesDir = new File(TEMPLATES_DIR);
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }

            File[] files = templatesDir.listFiles((dir, name) -> name.endsWith(".json"));

            List<Map<String, Object>> templateList = new ArrayList<>();
            if (files != null) {
                ObjectMapper mapper = new ObjectMapper();
                for (File file : files) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> template = mapper.readValue(file, Map.class);
                        template.put("fileName", file.getName());
                        templateList.add(template);
                    } catch (Exception e) {
                        logger.error("Error reading template file: {}", file.getName(), e);
                    }
                }
            }

            response.put("success", true);
            response.put("templates", templateList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error listing templates", e);
            response.put("success", false);
            response.put("message", "Error listing templates: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> loadTemplate(String fileName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid file name"));
            }

            File file = new File(TEMPLATES_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> template = mapper.readValue(file, Map.class);

            return ResponseEntity.ok(template);

        } catch (Exception e) {
            logger.error("Error loading template: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error loading template: " + e.getMessage()));
        }
    }

    public ResponseEntity<Map<String, Object>> deleteTemplate(String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.put("success", false);
                response.put("message", "Invalid file name");
                return ResponseEntity.badRequest().body(response);
            }

            File file = new File(TEMPLATES_DIR + fileName);
            if (!file.exists()) {
                response.put("success", false);
                response.put("message", "Template not found");
                return ResponseEntity.notFound().build();
            }

            if (file.delete()) {
                logger.info("Successfully deleted template: {}", fileName);
                response.put("success", true);
                response.put("message", "Template deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete template");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            logger.error("Error deleting template: {}", fileName, e);
            response.put("success", false);
            response.put("message", "Error deleting template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
