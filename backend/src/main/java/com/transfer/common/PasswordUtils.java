package com.transfer.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private PasswordUtils() {
    }

    /**
     * 推荐的新密码摘要格式：pbkdf2:iterations:base64(salt):base64(hash)
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(rawPassword.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);

        return "pbkdf2:" + PBKDF2_ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derived);
    }

    /**
     * 保留旧方法名，避免旧代码调用失效。现在实际返回 PBKDF2 摘要。
     */
    public static String sha256(String rawPassword) {
        return hash(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedPasswordHash) {
        if (rawPassword == null || storedPasswordHash == null || storedPasswordHash.isBlank()) {
            return false;
        }

        if (storedPasswordHash.startsWith("pbkdf2:")) {
            return matchesPbkdf2(rawPassword, storedPasswordHash);
        }

        // 兼容历史版本中已保存的 sha256:xxxx 密码摘要，避免旧账号无法登录。
        if (storedPasswordHash.startsWith("sha256:")) {
            return matchesLegacySha256(rawPassword, storedPasswordHash);
        }

        return false;
    }

    private static boolean matchesPbkdf2(String rawPassword, String storedPasswordHash) {
        String[] parts = storedPasswordHash.split(":");
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean matchesLegacySha256(String rawPassword, String storedPasswordHash) {
        String calculatedHash = legacySha256(rawPassword);
        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                storedPasswordHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String legacySha256(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 password hashing is not available", ex);
        }
    }
}
