package com.reportserver.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class TwoFactorAuthService {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorAuthService.class);
    private static final int SECRET_SIZE_BYTES = 20;
    private static final int TOTP_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base32 base32 = new Base32();

    @Value("${reportserver.security.2fa.issuer:ReportServer}")
    private String issuer;

    public String generateSecret() {
        byte[] secret = new byte[SECRET_SIZE_BYTES];
        secureRandom.nextBytes(secret);
        return base32.encodeToString(secret).replace("=", "");
    }

    public String buildOtpAuthUri(String username, String secret) {
        String encodedIssuer = urlEncode(issuer);
        String encodedUser = urlEncode(username);
        return "otpauth://totp/" + encodedIssuer + ":" + encodedUser
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + TOTP_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    public String generateQrCodeDataUri(String content) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 260, 260);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }

    public boolean verifyCode(String secret, String submittedCode) {
        if (secret == null || secret.isBlank()) {
            return false;
        }

        String normalizedCode = normalizeCode(submittedCode);
        if (!normalizedCode.matches("\\d{6}")) {
            return false;
        }

        long currentTimeWindow = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -1; offset <= 1; offset++) {
            String expected = generateCurrentNumber(secret, currentTimeWindow + offset);
            if (constantTimeEquals(expected, normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    private String generateCurrentNumber(String secret, long timeWindow) {
        try {
            byte[] decodedSecret = base32.decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodedSecret, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            logger.error("Failed to generate TOTP", e);
            return "";
        }
    }

    private String normalizeCode(String submittedCode) {
        if (submittedCode == null) {
            return "";
        }
        return submittedCode.replace(" ", "").trim();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
