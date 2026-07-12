package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.dto.CaptchaResponse;
import com.transfer.dto.EmailCodeResponse;
import com.transfer.enums.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CAPTCHA_WIDTH = 150;
    private static final int CAPTCHA_HEIGHT = 48;
    private static final int CAPTCHA_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CodeEntry> captchaStore = new ConcurrentHashMap<>();
    private final Map<String, CodeEntry> emailCodeStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> emailSendNextAllowedStore = new ConcurrentHashMap<>();
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;
    private final int captchaExpireSeconds;
    private final int emailCodeExpireSeconds;
    private final int emailCodeResendIntervalSeconds;
    private final boolean returnCodeInResponse;
    private final boolean consoleLogCode;

    public VerificationCodeService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@transfer.local}") String mailFrom,
            @Value("${app.verification.captcha-expire-seconds:120}") int captchaExpireSeconds,
            @Value("${app.verification.email-code-expire-seconds:300}") int emailCodeExpireSeconds,
            @Value("${app.verification.email-code-resend-interval-seconds:60}") int emailCodeResendIntervalSeconds,
            @Value("${app.verification.return-code-in-response:false}") boolean returnCodeInResponse,
            @Value("${app.verification.console-log-code:false}") boolean consoleLogCode
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom;
        this.captchaExpireSeconds = captchaExpireSeconds;
        this.emailCodeExpireSeconds = emailCodeExpireSeconds;
        this.emailCodeResendIntervalSeconds = emailCodeResendIntervalSeconds;
        this.returnCodeInResponse = returnCodeInResponse;
        this.consoleLogCode = consoleLogCode;
    }

    public CaptchaResponse createCaptcha() {
        cleanupExpired();

        String captchaId = UUID.randomUUID().toString();
        String code = randomText(CAPTCHA_LENGTH, CAPTCHA_CHARS);
        captchaStore.put(
                captchaId,
                new CodeEntry(hash(code), Instant.now().plusSeconds(captchaExpireSeconds))
        );

        return new CaptchaResponse(
                captchaId,
                "data:image/png;base64," + renderCaptchaBase64(code),
                captchaExpireSeconds
        );
    }

    public void validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BadRequestException("captchaId and captchaCode are required");
        }

        CodeEntry entry = captchaStore.remove(captchaId.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("图形验证码已过期，请重新获取");
        }

        if (!constantTimeEquals(entry.codeHash(), hash(captchaCode.trim().toUpperCase(Locale.ROOT)))) {
            throw new BadRequestException("图形验证码错误");
        }
    }

    public EmailCodeResponse sendEmailCode(
            VerificationPurpose purpose,
            String targetKey,
            String receiverEmail
    ) {
        if (purpose == null) {
            throw new BadRequestException("purpose is required");
        }
        if (targetKey == null || targetKey.isBlank()) {
            throw new BadRequestException("verification target is required");
        }
        if (receiverEmail == null || receiverEmail.isBlank()) {
            throw new BadRequestException("当前账号未绑定邮箱，无法发送邮箱验证码");
        }

        cleanupExpired();

        String storeKey = emailStoreKey(purpose, targetKey);
        Instant now = Instant.now();
        Instant nextAllowedAt;
        synchronized (emailSendNextAllowedStore) {
            nextAllowedAt = emailSendNextAllowedStore.get(storeKey);
            if (nextAllowedAt != null && nextAllowedAt.isAfter(now)) {
                long remainingSeconds = Math.max(1, Duration.between(now, nextAllowedAt).toSeconds());
                throw new BadRequestException("请求过于频繁，请 " + remainingSeconds + " 秒后重试");
            }
            nextAllowedAt = now.plusSeconds(emailCodeResendIntervalSeconds);
            emailSendNextAllowedStore.put(storeKey, nextAllowedAt);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        boolean sent = sendEmail(receiverEmail, purpose, code);
        if (!sent && !returnCodeInResponse) {
            emailSendNextAllowedStore.remove(storeKey, nextAllowedAt);
            throw new ExternalServiceException("邮箱验证码发送失败，请检查 SMTP 配置");
        }

        emailCodeStore.put(
                storeKey,
                new CodeEntry(hash(code), now.plusSeconds(emailCodeExpireSeconds))
        );
        if (consoleLogCode) {
            log.info("Email verification code purpose={}, target={}, receiver={}, code={}",
                    purpose, targetKey, receiverEmail, code);
        }

        return new EmailCodeResponse(
                "邮箱验证码已发送，请在有效期内完成验证",
                emailCodeExpireSeconds,
                returnCodeInResponse ? code : null
        );
    }

    public void validateEmailCode(
            VerificationPurpose purpose,
            String targetKey,
            String emailCode
    ) {
        if (purpose == null) {
            throw new BadRequestException("purpose is required");
        }
        if (targetKey == null || targetKey.isBlank()) {
            throw new BadRequestException("verification target is required");
        }
        if (emailCode == null || emailCode.isBlank()) {
            throw new BadRequestException("emailCode is required");
        }

        String storeKey = emailStoreKey(purpose, targetKey);
        CodeEntry entry = emailCodeStore.remove(storeKey);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("邮箱验证码已过期，请重新获取");
        }

        if (!constantTimeEquals(entry.codeHash(), hash(emailCode.trim()))) {
            throw new BadRequestException("邮箱验证码错误");
        }
    }

    public String emailTargetKey(VerificationPurpose purpose, String target) {
        return purpose.name() + ":" + target.trim().toLowerCase(Locale.ROOT);
    }

    private boolean sendEmail(String receiverEmail, VerificationPurpose purpose, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured; skip real email sending");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(receiverEmail);
            message.setSubject("交通事故风险系统邮箱验证码");
            message.setText("你的验证码是：" + code + "。用途：" + purpose.name()
                    + "。验证码 " + emailCodeExpireSeconds / 60 + " 分钟内有效，请勿转发给他人。");
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send email verification code to {}", receiverEmail, ex);
            return false;
        }
    }

    private String emailStoreKey(VerificationPurpose purpose, String targetKey) {
        return purpose.name() + ":" + targetKey.trim().toLowerCase(Locale.ROOT);
    }

    private String renderCaptchaBase64(String code) {
        try {
            BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(246, 248, 252));
            graphics.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

            for (int i = 0; i < 12; i++) {
                graphics.setColor(randomSoftColor());
                graphics.setStroke(new BasicStroke(1.2f));
                int x1 = random.nextInt(CAPTCHA_WIDTH);
                int y1 = random.nextInt(CAPTCHA_HEIGHT);
                int x2 = random.nextInt(CAPTCHA_WIDTH);
                int y2 = random.nextInt(CAPTCHA_HEIGHT);
                graphics.drawLine(x1, y1, x2, y2);
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(25 + random.nextInt(80), 40 + random.nextInt(80), 80 + random.nextInt(80)));
                double angle = Math.toRadians(random.nextInt(36) - 18);
                graphics.rotate(angle, 24 + i * 24, 32);
                graphics.drawString(String.valueOf(code.charAt(i)), 14 + i * 26, 34 + random.nextInt(5));
                graphics.rotate(-angle, 24 + i * 24, 32);
            }
            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render captcha image", ex);
        }
    }

    private Color randomSoftColor() {
        int base = 150 + random.nextInt(80);
        return new Color(base, base + random.nextInt(Math.max(1, 255 - base)), base + random.nextInt(Math.max(1, 255 - base)));
    }

    private String randomText(int length, String chars) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        emailCodeStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        emailSendNextAllowedStore.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private record CodeEntry(
            String codeHash,
            Instant expiresAt
    ) {
    }
}
