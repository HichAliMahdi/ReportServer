package com.reportserver.service;

import com.reportserver.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final SmtpSettingsService smtpSettingsService;

    @Value("${reportserver.app.base-url:}")
    private String appBaseUrl;

    public EmailNotificationService(SmtpSettingsService smtpSettingsService) {
        this.smtpSettingsService = smtpSettingsService;
    }

    public void sendPasswordResetLink(User user, String token) {
        sendPasswordResetLink(user, token, appBaseUrl);
    }

    public void sendPasswordResetLink(User user, String token, String baseUrl) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String effectiveBaseUrl = normalizeBaseUrl(baseUrl);
        String resetLink = effectiveBaseUrl + "/reset-password?token=" + urlEncode(token);
        String body = "Hello " + user.getUsername() + ",\n\n"
                + "A password reset was requested for your account.\n"
                + "Use the link below to set a new password:\n\n"
                + resetLink + "\n\n"
                + "This link expires in 1 hour. If you did not request this, you can safely ignore this email.\n\n"
                + "Report Server";

        sendSimpleMail(user.getEmail(), "Report Server - Password Reset", body);
    }

    public void sendPasswordChangedNotice(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String body = "Hello " + user.getUsername() + ",\n\n"
                + "Your password has been changed successfully.\n"
                + "If this was not you, please contact an administrator immediately.\n\n"
                + "Report Server";

        sendSimpleMail(user.getEmail(), "Report Server - Password Changed", body);
    }

    public void sendTwoFactorActivationNotice(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String body = "Hello " + user.getUsername() + ",\n\n"
                + "Two-factor authentication (2FA) has been activated for your account by an administrator.\n"
                + "Scan the QR code from your account security screen and verify your authenticator code.\n\n"
                + "Report Server";

        sendSimpleMail(user.getEmail(), "Report Server - 2FA Activated", body);
    }

    public void sendTwoFactorConfirmedNotice(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String body = "Hello " + user.getUsername() + ",\n\n"
                + "Two-factor authentication is now fully enabled on your account.\n"
                + "Future logins will require your authenticator app code.\n\n"
                + "Report Server";

        sendSimpleMail(user.getEmail(), "Report Server - 2FA Enabled", body);
    }

    public void sendWelcomeEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String body = "Hello " + user.getUsername() + ",\n\n"
                + "Your Reports Server account is ready.\n"
                + "You can log in and start configuring reports, users, and security settings.\n\n"
                + "Report Server";

        sendSimpleMail(user.getEmail(), "Welcome to Reports Server", body);
    }

    public void sendTestEmail(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        String body = "This is a test email from Report Server SMTP settings page.\n\n"
                + "If you received this, SMTP configuration is valid.";
        sendSimpleMail(recipient.trim(), "Report Server - SMTP Test", body);
    }

    private void sendSimpleMail(String to, String subject, String body) {
        try {
            JavaMailSender sender = smtpSettingsService.buildMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpSettingsService.resolveFromEmail());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            logger.info("Email notification sent to {} with subject '{}'", to, subject);
        } catch (Exception e) {
            logger.error("Failed to send email notification to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeBaseUrl(String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.isEmpty()) {
            value = appBaseUrl == null ? "" : appBaseUrl.trim();
        }
        if (value.isEmpty()) {
            throw new IllegalStateException("Application base URL is not configured");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
