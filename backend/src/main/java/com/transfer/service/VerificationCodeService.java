package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.dto.EmailCodeResponse;
import com.transfer.enums.VerificationPurpose;
import com.transfer.verification.VerificationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);

    private final SecureRandom random = new SecureRandom();
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final VerificationStore redisStore;
    private final String mailFrom;
    private final int emailCodeExpireSeconds;
    private final int emailCodeResendIntervalSeconds;
    private final int emailCodeMaxAttempts;
    private final boolean returnCodeInResponse;
    private final boolean consoleLogCode;

    public VerificationCodeService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            VerificationStore redisStore,
            @Value("${app.mail.from:no-reply@transfer.local}") String mailFrom,
            @Value("${app.verification.email-code-expire-seconds:300}") int emailCodeExpireSeconds,
            @Value("${app.verification.email-code-resend-interval-seconds:60}") int emailCodeResendIntervalSeconds,
            @Value("${app.verification.email-code-max-attempts:5}") int emailCodeMaxAttempts,
            @Value("${app.verification.return-code-in-response:false}") boolean returnCodeInResponse,
            @Value("${app.verification.console-log-code:false}") boolean consoleLogCode
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.redisStore = redisStore;
        this.mailFrom = mailFrom;
        this.emailCodeExpireSeconds = emailCodeExpireSeconds;
        this.emailCodeResendIntervalSeconds = emailCodeResendIntervalSeconds;
        this.emailCodeMaxAttempts = emailCodeMaxAttempts;
        this.returnCodeInResponse = returnCodeInResponse;
        this.consoleLogCode = consoleLogCode;
    }

    public EmailCodeResponse sendEmailCode(
            VerificationPurpose purpose,
            String targetKey,
            String receiverEmail
    ) {
        if (purpose == null) {
            throw new BadRequestException("purpose is required");
        }
        String normalizedTarget = normalizeTarget(targetKey);
        if (receiverEmail == null || receiverEmail.isBlank()) {
            throw new BadRequestException("当前账号未绑定邮箱，无法发送邮箱验证码");
        }

        String cooldownKey = emailCooldownKey(purpose, normalizedTarget);
        boolean allowed = redisStore.setIfAbsent(
                cooldownKey,
                "1",
                Duration.ofSeconds(emailCodeResendIntervalSeconds)
        );
        if (!allowed) {
            Duration remaining = redisStore.getTtl(cooldownKey);
            long seconds = remaining == null ? emailCodeResendIntervalSeconds : Math.max(1, remaining.toSeconds());
            throw new BadRequestException("请求过于频繁，请 " + seconds + " 秒后重试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        boolean sent = sendEmail(receiverEmail, purpose, code);
        if (!sent && !returnCodeInResponse) {
            redisStore.delete(cooldownKey);
            throw new ExternalServiceException("邮箱验证码发送失败，请检查 SMTP 配置");
        }

        redisStore.setJson(
                emailCodeKey(purpose, normalizedTarget),
                new EmailCodeState(hash(code), 0),
                Duration.ofSeconds(emailCodeExpireSeconds)
        );

        if (consoleLogCode) {
            log.info("Email verification code purpose={}, targetHash={}, receiver={}, code={}",
                    purpose, sha256KeyPart(normalizedTarget), receiverEmail, code);
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
        String normalizedTarget = normalizeTarget(targetKey);
        if (emailCode == null || emailCode.isBlank()) {
            throw new BadRequestException("emailCode is required");
        }

        String key = emailCodeKey(purpose, normalizedTarget);
        EmailCodeState state = redisStore.getJson(key, EmailCodeState.class);
        if (state == null) {
            throw new BadRequestException("邮箱验证码已过期，请重新获取");
        }

        if (!constantTimeEquals(state.codeHash(), hash(emailCode.trim()))) {
            int attempts = state.attempts() + 1;
            if (attempts >= emailCodeMaxAttempts) {
                redisStore.delete(key);
                throw new BadRequestException("邮箱验证码错误次数过多，请重新获取");
            }
            redisStore.updateJsonPreservingTtl(key, new EmailCodeState(state.codeHash(), attempts));
            throw new BadRequestException("邮箱验证码错误");
        }

        redisStore.delete(key);
    }

    public String emailTargetKey(VerificationPurpose purpose, String target) {
        if (purpose == null) {
            throw new BadRequestException("purpose is required");
        }
        return normalizeTarget(target);
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
                    + "。验证码 " + Math.max(1, emailCodeExpireSeconds / 60)
                    + " 分钟内有效，请勿转发给他人。");
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send email verification code to {}", receiverEmail, ex);
            return false;
        }
    }

    private String emailCodeKey(VerificationPurpose purpose, String normalizedTarget) {
        return redisStore.key(
                "verify:email:code:" + purpose.name() + ":" + sha256KeyPart(normalizedTarget)
        );
    }

    private String emailCooldownKey(VerificationPurpose purpose, String normalizedTarget) {
        return redisStore.key(
                "verify:email:cooldown:" + purpose.name() + ":" + sha256KeyPart(normalizedTarget)
        );
    }

    private String normalizeTarget(String targetKey) {
        if (targetKey == null || targetKey.isBlank()) {
            throw new BadRequestException("verification target is required");
        }
        return targetKey.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256KeyPart(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
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

    public record EmailCodeState(
            String codeHash,
            int attempts
    ) {
    }
}
