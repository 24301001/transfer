package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.dto.SliderCaptchaChallengeResponse;
import com.transfer.dto.SliderCaptchaVerifyResponse;
import com.transfer.verification.VerificationStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class SliderCaptchaService {

    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 160;
    private static final int PIECE_SIZE = 52;

    private final SecureRandom random = new SecureRandom();
    private final VerificationStore redisStore;
    private final int challengeExpireSeconds;
    private final int tokenExpireSeconds;
    private final int tolerancePixels;
    private final int maxAttempts;

    public SliderCaptchaService(
            VerificationStore redisStore,
            @Value("${app.verification.slider-challenge-expire-seconds:120}") int challengeExpireSeconds,
            @Value("${app.verification.slider-token-expire-seconds:120}") int tokenExpireSeconds,
            @Value("${app.verification.slider-tolerance-pixels:6}") int tolerancePixels,
            @Value("${app.verification.slider-max-attempts:5}") int maxAttempts
    ) {
        this.redisStore = redisStore;
        this.challengeExpireSeconds = challengeExpireSeconds;
        this.tokenExpireSeconds = tokenExpireSeconds;
        this.tolerancePixels = tolerancePixels;
        this.maxAttempts = maxAttempts;
    }

    public SliderCaptchaChallengeResponse createChallenge(String fingerprintHash) {
        int targetX = 72 + random.nextInt(IMAGE_WIDTH - PIECE_SIZE - 92);
        int targetY = 22 + random.nextInt(IMAGE_HEIGHT - PIECE_SIZE - 44);
        String captchaId = UUID.randomUUID().toString();

        RenderedSlider rendered = render(targetX, targetY);
        SliderChallengeState state = new SliderChallengeState(targetX, fingerprintHash, 0);
        redisStore.setJson(
                challengeKey(captchaId),
                state,
                Duration.ofSeconds(challengeExpireSeconds)
        );

        return new SliderCaptchaChallengeResponse(
                captchaId,
                "data:image/png;base64," + rendered.backgroundBase64(),
                "data:image/png;base64," + rendered.puzzleBase64(),
                targetY,
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                PIECE_SIZE,
                challengeExpireSeconds
        );
    }

    public SliderCaptchaVerifyResponse verify(
            String captchaId,
            int sliderX,
            String fingerprintHash
    ) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BadRequestException("captchaId is required");
        }
        if (sliderX < 0 || sliderX > IMAGE_WIDTH - PIECE_SIZE) {
            throw new BadRequestException("滑块位置超出允许范围");
        }

        String key = challengeKey(captchaId.trim());
        SliderChallengeState state = redisStore.getJson(key, SliderChallengeState.class);
        if (state == null) {
            throw new BadRequestException("滑块验证码已过期，请重新获取");
        }
        if (!constantTimeEquals(state.fingerprintHash(), fingerprintHash)) {
            redisStore.delete(key);
            throw new BadRequestException("验证环境发生变化，请重新获取滑块验证码");
        }

        if (Math.abs(state.targetX() - sliderX) > tolerancePixels) {
            int attempts = state.attempts() + 1;
            if (attempts >= maxAttempts) {
                redisStore.delete(key);
                throw new BadRequestException("滑块验证失败次数过多，请重新获取");
            }
            redisStore.updateJsonPreservingTtl(
                    key,
                    new SliderChallengeState(state.targetX(), state.fingerprintHash(), attempts)
            );
            throw new BadRequestException("滑块位置不正确，请重试");
        }

        redisStore.delete(key);
        String sliderToken = UUID.randomUUID() + "." + UUID.randomUUID();
        redisStore.setString(
                sliderTokenKey(sliderToken),
                fingerprintHash,
                Duration.ofSeconds(tokenExpireSeconds)
        );
        return new SliderCaptchaVerifyResponse(sliderToken, tokenExpireSeconds);
    }

    public void consumeVerificationToken(String sliderToken, String fingerprintHash) {
        if (sliderToken == null || sliderToken.isBlank()) {
            throw new BadRequestException("sliderToken is required");
        }
        long result = redisStore.consumeIfEquals(
                sliderTokenKey(sliderToken.trim()),
                fingerprintHash
        );
        if (result == 0L) {
            throw new BadRequestException("滑块验证凭证已过期或已使用，请重新验证");
        }
        if (result < 0L) {
            throw new BadRequestException("滑块验证凭证与当前客户端不匹配");
        }
    }

    private RenderedSlider render(int targetX, int targetY) {
        try {
            BufferedImage original = buildBackground();
            BufferedImage background = copyImage(original, BufferedImage.TYPE_INT_RGB);
            BufferedImage puzzle = new BufferedImage(PIECE_SIZE, PIECE_SIZE, BufferedImage.TYPE_INT_ARGB);

            Area localShape = puzzleShape();
            Area worldShape = localShape.createTransformedArea(
                    AffineTransform.getTranslateInstance(targetX, targetY)
            );

            Graphics2D puzzleGraphics = puzzle.createGraphics();
            configureGraphics(puzzleGraphics);
            puzzleGraphics.setClip(localShape);
            puzzleGraphics.drawImage(original, -targetX, -targetY, null);
            puzzleGraphics.setClip(null);
            puzzleGraphics.setComposite(AlphaComposite.SrcOver);
            puzzleGraphics.setColor(new Color(255, 255, 255, 210));
            puzzleGraphics.setStroke(new BasicStroke(2f));
            puzzleGraphics.draw(localShape);
            puzzleGraphics.dispose();

            Graphics2D backgroundGraphics = background.createGraphics();
            configureGraphics(backgroundGraphics);
            backgroundGraphics.setColor(new Color(255, 255, 255, 178));
            backgroundGraphics.fill(worldShape);
            backgroundGraphics.setColor(new Color(38, 63, 95, 150));
            backgroundGraphics.setStroke(new BasicStroke(2f));
            backgroundGraphics.draw(worldShape);
            backgroundGraphics.dispose();

            return new RenderedSlider(toBase64(background), toBase64(puzzle));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render slider captcha", ex);
        }
    }

    private BufferedImage buildBackground() {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        configureGraphics(graphics);

        Color start = new Color(70 + random.nextInt(80), 110 + random.nextInt(80), 150 + random.nextInt(70));
        Color end = new Color(190 + random.nextInt(55), 200 + random.nextInt(45), 210 + random.nextInt(40));
        graphics.setPaint(new GradientPaint(0, 0, start, IMAGE_WIDTH, IMAGE_HEIGHT, end));
        graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        for (int i = 0; i < 18; i++) {
            int size = 18 + random.nextInt(62);
            int x = random.nextInt(IMAGE_WIDTH);
            int y = random.nextInt(IMAGE_HEIGHT);
            graphics.setColor(new Color(
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256),
                    35 + random.nextInt(65)
            ));
            graphics.fillOval(x - size / 2, y - size / 2, size, size);
        }

        for (int i = 0; i < 8; i++) {
            graphics.setColor(new Color(255, 255, 255, 35 + random.nextInt(60)));
            graphics.setStroke(new BasicStroke(1f + random.nextFloat() * 3f));
            int x1 = random.nextInt(IMAGE_WIDTH);
            int y1 = random.nextInt(IMAGE_HEIGHT);
            int x2 = random.nextInt(IMAGE_WIDTH);
            int y2 = random.nextInt(IMAGE_HEIGHT);
            graphics.drawLine(x1, y1, x2, y2);
        }

        graphics.dispose();
        return image;
    }

    private Area puzzleShape() {
        int pad = 7;
        int bodySize = PIECE_SIZE - pad * 2;
        Area shape = new Area(new RoundRectangle2D.Double(pad, pad, bodySize, bodySize, 9, 9));
        double radius = 8;
        shape.add(new Area(new Ellipse2D.Double(
                PIECE_SIZE / 2.0 - radius,
                pad - radius,
                radius * 2,
                radius * 2
        )));
        shape.subtract(new Area(new Ellipse2D.Double(
                PIECE_SIZE - pad - radius,
                PIECE_SIZE / 2.0 - radius,
                radius * 2,
                radius * 2
        )));
        return shape;
    }

    private BufferedImage copyImage(BufferedImage source, int type) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private String toBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private String challengeKey(String captchaId) {
        return redisStore.key("verify:slider:challenge:" + captchaId);
    }

    private String sliderTokenKey(String token) {
        return redisStore.key("verify:slider:token:" + sha256(token));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
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

    public record SliderChallengeState(
            int targetX,
            String fingerprintHash,
            int attempts
    ) {
    }

    private record RenderedSlider(
            String backgroundBase64,
            String puzzleBase64
    ) {
    }
}
