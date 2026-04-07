package com.reportserver.service;

import com.reportserver.model.SmtpSettings;
import com.reportserver.repository.SmtpSettingsRepository;
import com.reportserver.security.PasswordEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Service
public class SmtpSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SmtpSettingsService.class);

    private final SmtpSettingsRepository smtpSettingsRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Value("${spring.mail.host:smtp.example.com}")
    private String fallbackHost;

    @Value("${spring.mail.port:587}")
    private int fallbackPort;

    @Value("${spring.mail.username:noreply@reportserver.local}")
    private String fallbackUsername;

    @Value("${spring.mail.password:}")
    private String fallbackPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean fallbackAuthEnabled;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean fallbackStarttlsEnabled;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private boolean fallbackStarttlsRequired;

    public SmtpSettingsService(SmtpSettingsRepository smtpSettingsRepository,
                               PasswordEncryptor passwordEncryptor) {
        this.smtpSettingsRepository = smtpSettingsRepository;
        this.passwordEncryptor = passwordEncryptor;
    }

    public Map<String, Object> getSettingsForAdmin() {
        Optional<SmtpSettings> settingsOpt = smtpSettingsRepository.findTopByOrderByIdAsc();
        Map<String, Object> response = new LinkedHashMap<>();

        if (settingsOpt.isEmpty()) {
            response.put("configured", false);
            response.put("enabled", false);
            response.put("host", fallbackHost);
            response.put("port", fallbackPort);
            response.put("username", fallbackUsername);
            response.put("fromEmail", fallbackUsername);
            response.put("authEnabled", fallbackAuthEnabled);
            response.put("starttlsEnabled", fallbackStarttlsEnabled);
            response.put("starttlsRequired", fallbackStarttlsRequired);
            response.put("hasPassword", fallbackPassword != null && !fallbackPassword.isBlank());
            return response;
        }

        SmtpSettings s = settingsOpt.get();
        response.put("configured", true);
        response.put("enabled", s.isEnabled());
        response.put("host", s.getHost());
        response.put("port", s.getPort());
        response.put("username", s.getUsername());
        response.put("fromEmail", resolveFromEmail(s));
        response.put("authEnabled", s.isAuthEnabled());
        response.put("starttlsEnabled", s.isStarttlsEnabled());
        response.put("starttlsRequired", s.isStarttlsRequired());
        response.put("hasPassword", s.getPasswordEncrypted() != null && !s.getPasswordEncrypted().isBlank());
        response.put("updatedAt", s.getUpdatedAt());
        response.put("updatedBy", s.getUpdatedBy());
        return response;
    }

    public void saveSettings(Map<String, Object> request, String updatedBy) {
        String host = asString(request.get("host"));
        Integer port = asInteger(request.get("port"));
        String username = asString(request.get("username"));
        String fromEmail = asString(request.get("fromEmail"));
        String password = asString(request.get("password"));

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("SMTP host is required");
        }
        if (port == null || port <= 0) {
            throw new IllegalArgumentException("SMTP port must be a positive number");
        }

        SmtpSettings settings = smtpSettingsRepository.findTopByOrderByIdAsc().orElseGet(SmtpSettings::new);
        settings.setHost(host.trim());
        settings.setPort(port);
        settings.setUsername(username == null ? null : username.trim());
        settings.setFromEmail(fromEmail == null ? null : fromEmail.trim());
        settings.setAuthEnabled(asBoolean(request.get("authEnabled"), true));
        settings.setStarttlsEnabled(asBoolean(request.get("starttlsEnabled"), true));
        settings.setStarttlsRequired(asBoolean(request.get("starttlsRequired"), true));
        settings.setEnabled(asBoolean(request.get("enabled"), true));

        if (password != null && !password.isBlank()) {
            settings.setPasswordEncrypted(passwordEncryptor.encrypt(password));
        }

        settings.setUpdatedBy(updatedBy);
        smtpSettingsRepository.save(settings);
        logger.info("SMTP settings saved by {}", updatedBy);
    }

    public void testConnection(Map<String, Object> request, String testRecipient) {
        String host = asString(request.get("host"));
        Integer port = asInteger(request.get("port"));
        String username = asString(request.get("username"));
        String fromEmail = asString(request.get("fromEmail"));
        String password = asString(request.get("password"));

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("SMTP host is required");
        }
        if (port == null || port <= 0) {
            throw new IllegalArgumentException("SMTP port must be a positive number");
        }
        if (testRecipient == null || testRecipient.isBlank()) {
            throw new IllegalArgumentException("Test recipient email is required");
        }

        // Create a temporary JavaMailSender with the provided settings
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);
        sender.setUsername(username == null ? null : username.trim());
        sender.setPassword(password == null ? "" : password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(asBoolean(request.get("authEnabled"), true)));
        props.put("mail.smtp.starttls.enable", String.valueOf(asBoolean(request.get("starttlsEnabled"), true)));
        props.put("mail.smtp.starttls.required", String.valueOf(asBoolean(request.get("starttlsRequired"), true)));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.debug", "false");

        try {
            // Send a test email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail == null || fromEmail.isBlank() ? username : fromEmail);
            message.setTo(testRecipient.trim());
            message.setSubject("Report Server - SMTP Test Email");
            message.setText("This is a test email from Reports Server SMTP configuration. If you received this, your SMTP settings are working correctly.");

            sender.send(message);
            logger.info("Test email sent successfully to {}", testRecipient);
        } catch (Exception e) {
            logger.error("Failed to send test email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send test email: " + e.getMessage(), e);
        }
    }

    public JavaMailSender buildMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        SmtpSettings s = smtpSettingsRepository.findTopByOrderByIdAsc().orElse(null);

        if (s != null && s.isEnabled()) {
            sender.setHost(s.getHost());
            sender.setPort(s.getPort());
            sender.setUsername(s.getUsername());
            sender.setPassword(decryptPassword(s.getPasswordEncrypted()));

            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", String.valueOf(s.isAuthEnabled()));
            props.put("mail.smtp.starttls.enable", String.valueOf(s.isStarttlsEnabled()));
            props.put("mail.smtp.starttls.required", String.valueOf(s.isStarttlsRequired()));
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.debug", "false");
            return sender;
        }

        sender.setHost(fallbackHost);
        sender.setPort(fallbackPort);
        sender.setUsername(fallbackUsername);
        sender.setPassword(fallbackPassword);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(fallbackAuthEnabled));
        props.put("mail.smtp.starttls.enable", String.valueOf(fallbackStarttlsEnabled));
        props.put("mail.smtp.starttls.required", String.valueOf(fallbackStarttlsRequired));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.debug", "false");
        return sender;
    }

    public String resolveFromEmail() {
        SmtpSettings s = smtpSettingsRepository.findTopByOrderByIdAsc().orElse(null);
        if (s == null || !s.isEnabled()) {
            return fallbackUsername;
        }
        return resolveFromEmail(s);
    }

    private String resolveFromEmail(SmtpSettings settings) {
        if (settings.getFromEmail() != null && !settings.getFromEmail().isBlank()) {
            return settings.getFromEmail();
        }
        if (settings.getUsername() != null && !settings.getUsername().isBlank()) {
            return settings.getUsername();
        }
        return fallbackUsername;
    }

    private String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return "";
        }
        return passwordEncryptor.decrypt(encryptedPassword);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
