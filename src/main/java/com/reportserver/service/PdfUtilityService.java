package com.reportserver.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * Service for PDF-related operations.
 * Encapsulates PDFBox usage away from controllers.
 */
@Service
public class PdfUtilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(PdfUtilityService.class);
    
    /**
     * Get the page count of a PDF file.
     * 
     * @param file The PDF file to read
     * @return The number of pages in the PDF, or -1 if the file cannot be read
     * @throws IOException If there's an error reading the PDF
     */
    public int getPdfPageCount(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("PDF file does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
        }
        
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            throw new IOException("File is not a PDF: " + file.getName());
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            int pageCount = document.getNumberOfPages();
            logger.debug("PDF page count for {}: {}", file.getName(), pageCount);
            return pageCount;
        } catch (IOException e) {
            logger.error("Error reading PDF file {}", file.getAbsolutePath(), e);
            throw new IOException("Failed to read PDF: " + e.getMessage(), e);
        }
    }
}
