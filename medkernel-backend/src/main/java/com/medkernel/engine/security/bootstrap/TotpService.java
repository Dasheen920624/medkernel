package com.medkernel.engine.security.bootstrap;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * TOTP 一次性验证码服务，遵循 RFC 6238：30 秒窗口、6 位数字、HMAC-SHA1。
 */
@Service
public class TotpService {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, Instant.now());
    }

    boolean verify(String secret, String code, Instant now) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return false;
        }
        long counter = now.getEpochSecond() / STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (normalized.equals(codeAt(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    String codeAt(String secret, Instant at) {
        return codeAt(secret, at.getEpochSecond() / STEP_SECONDS);
    }

    public String otpauthUri(String issuer, String accountName, String secret) {
        String normalizedIssuer = issuer == null || issuer.isBlank() ? "MedKernel" : issuer.trim();
        String normalizedAccount = accountName == null || accountName.isBlank() ? "platform-user" : accountName.trim();
        return "otpauth://totp/"
            + url(normalizedIssuer + ":" + normalizedAccount)
            + "?secret=" + url(secret)
            + "&issuer=" + url(normalizedIssuer)
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private String codeAt(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] message = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return "";
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().replace(" ", "");
        if (!normalized.matches("\\d{6}")) {
            return null;
        }
        return normalized;
    }

    private String base32Encode(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return result.toString();
    }

    private byte[] base32Decode(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TOTP secret 不能为空");
        }
        String normalized = text.trim().replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < normalized.length(); i++) {
            int value = base32Value(normalized.charAt(i));
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private int base32Value(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch - 'A';
        }
        if (ch >= '2' && ch <= '7') {
            return ch - '2' + 26;
        }
        throw new IllegalArgumentException("非法 TOTP secret 字符");
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
