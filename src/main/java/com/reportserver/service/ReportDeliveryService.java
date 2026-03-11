package com.reportserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.ReportDeliveryOption;
import com.reportserver.repository.ReportDeliveryOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class ReportDeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(ReportDeliveryService.class);

    private final ReportDeliveryOptionRepository deliveryOptionRepository;
    private final JavaMailSender mailSender;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username:noreply@reportserver.local}")
    private String fromEmail;

    @Value("${report.delivery.webhook.timeout:30000}")
    private long webhookTimeout; // ms

    @Value("${report.delivery.webhook.retries:3}")
    private int webhookRetries;

    public ReportDeliveryService(
            ReportDeliveryOptionRepository deliveryOptionRepository,
            JavaMailSender mailSender,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.deliveryOptionRepository = deliveryOptionRepository;
        this.mailSender = mailSender;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute all enabled deliveries for a schedule
     */
    public void deliverScheduledReport(Long scheduleId, String reportName, File reportFile, String format) {
        List<ReportDeliveryOption> deliveries = 
            deliveryOptionRepository.findByScheduleIdAndEnabledTrue(scheduleId);

        for (ReportDeliveryOption delivery : deliveries) {
            try {
                if (delivery.getType() == ReportDeliveryOption.DeliveryType.EMAIL) {
                    deliverViaEmail(delivery, reportName, reportFile, format);
                } else if (delivery.getType() == ReportDeliveryOption.DeliveryType.WEBHOOK) {
                    deliverViaWebhook(delivery, reportName, reportFile, format);
                }
            } catch (Exception e) {
                logger.error("Failed to deliver report {} via {}: {}",
                    reportName, delivery.getType(), e.getMessage(), e);
            }
        }
    }

    /**
     * Send report via email to specified recipients
     */
    private void deliverViaEmail(ReportDeliveryOption delivery, String reportName, File reportFile, String format) {
        try {
            String[] recipients = parseRecipients(delivery.getRecipientOrUrl());
            if (recipients.length == 0) {
                logger.warn("No email recipients configured for Schedule ID {}", delivery.getScheduleId());
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(recipients);
            helper.setSubject("Report Generated: " + reportName);

            String body = String.format(
                "Your scheduled report '%s' has been generated.\n\n" +
                "Format: %s\n" +
                "File: %s\n\n" +
                "The report is attached to this email.\n\n" +
                "Report Server",
                reportName, format.toUpperCase(), reportFile.getName()
            );
            helper.setText(body);

            // Attach report file
            FileSystemResource fileResource = new FileSystemResource(reportFile);
            helper.addAttachment(reportFile.getName(), fileResource);

            mailSender.send(message);
            logger.info("Sent report {} via email to {} recipients", reportName, recipients.length);

        } catch (Exception e) {
            logger.error("Failed to send email for report {}: {}", reportName, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }

    /**
     * Send report via webhook POST to specified URL
     */
    private void deliverViaWebhook(ReportDeliveryOption delivery, String reportName, File reportFile, String format) {
        try {
            String webhookUrl = delivery.getRecipientOrUrl();
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                logger.warn("No webhook URL configured for Schedule ID {}", delivery.getScheduleId());
                return;
            }

            // Parse metadata for additional headers/auth
            Map<String, Object> metadata = new java.util.HashMap<>();
            if (delivery.getMetadata() != null) {
                try {
                    metadata = objectMapper.readValue(delivery.getMetadata(), Map.class);
                } catch (Exception e) {
                    logger.warn("Failed to parse webhook metadata for Schedule {}", delivery.getScheduleId());
                }
            }

            // Send HTTP POST with file
            byte[] fileBytes = java.nio.file.Files.readAllBytes(reportFile.toPath());

            String payload = objectMapper.writeValueAsString(Map.of(
                "reportName", reportName,
                "format", format,
                "fileSize", fileBytes.length,
                "timestamp", System.currentTimeMillis()
            ));

            WebClient client = webClientBuilder.build();
            for (int attempt = 0; attempt < webhookRetries; attempt++) {
                try {
                    client.post()
                        .uri(webhookUrl)
                        .header("Content-Type", "application/json")
                        .header("X-Report-Name", reportName)
                        .header("X-Report-Format", format)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(java.time.Duration.ofMillis(webhookTimeout));

                    logger.info("Successfully delivered report {} to webhook {}", reportName, webhookUrl);
                    return;

                } catch (Exception e) {
                    logger.warn("Webhook delivery attempt {}/{} failed for {}: {}",
                        attempt + 1, webhookRetries, webhookUrl, e.getMessage());
                    if (attempt < webhookRetries - 1) {
                        Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    }
                }
            }
            throw new RuntimeException("Webhook delivery failed after " + webhookRetries + " attempts");

        } catch (Exception e) {
            logger.error("Failed to send webhook for report {}: {}", reportName, e.getMessage(), e);
            throw new RuntimeException("Webhook delivery failed", e);
        }
    }

    /**
     * Parse email recipients from comma-separated or JSON format
     */
    private String[] parseRecipients(String recipientConfig) {
        if (recipientConfig == null || recipientConfig.isEmpty()) {
            return new String[0];
        }

        // Simple comma-separated format or JSON array
        if (recipientConfig.startsWith("[")) {
            try {
                List<Map<String, String>> recipients = objectMapper.readValue(
                    recipientConfig,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {}
                );
                return recipients.stream()
                    .map(r -> r.getOrDefault("email", r.getOrDefault("value", "")))
                    .filter(e -> !e.isEmpty())
                    .toArray(String[]::new);
            } catch (Exception e) {
                logger.warn("Failed to parse JSON recipients: {}", e.getMessage());
            }
        }

        // Fall back to comma-separated
        return recipientConfig.split(",\\s*");
    }

    /**
     * Create a delivery option
     */
    public ReportDeliveryOption createDeliveryOption(Long scheduleId, ReportDeliveryOption.DeliveryType type,
                                                      String recipientOrUrl, String metadata) {
        ReportDeliveryOption option = new ReportDeliveryOption();
        option.setScheduleId(scheduleId);
        option.setType(type);
        option.setRecipientOrUrl(recipientOrUrl);
        option.setMetadata(metadata);
        option.setEnabled(true);
        return deliveryOptionRepository.save(option);
    }

    /**
     * Get all delivery options for a schedule
     */
    public List<ReportDeliveryOption> getDeliveryOptions(Long scheduleId) {
        return deliveryOptionRepository.findByScheduleId(scheduleId);
    }

    /**
     * Update a delivery option
     */
    public ReportDeliveryOption updateDeliveryOption(Long deliveryId, Boolean enabled, String recipientOrUrl) {
        ReportDeliveryOption option = deliveryOptionRepository.findById(deliveryId)
            .orElseThrow(() -> new RuntimeException("Delivery option not found"));
        if (enabled != null) option.setEnabled(enabled);
        if (recipientOrUrl != null) option.setRecipientOrUrl(recipientOrUrl);
        return deliveryOptionRepository.save(option);
    }

    /**
     * Delete a delivery option
     */
    public void deleteDeliveryOption(Long deliveryId) {
        deliveryOptionRepository.deleteById(deliveryId);
    }
}
