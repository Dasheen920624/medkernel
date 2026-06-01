package com.medkernel.engine.security.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.medkernel.shared.config.AuthPasswordHashAlgorithm;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 平台账号口令哈希统一入口：BCrypt 为默认基线，配置中心切换后新哈希写入带盐 SM3。
 */
@Service
public class CredentialPasswordService {

    private static final String SM3_PREFIX = "sm3:v1:";
    private static final int SM3_ITERATIONS = 60_000;
    private static final int SALT_BYTES = 16;
    private static final String DUMMY_PASSWORD = "__medkernel_dummy_account__";

    private final PasswordEncoder bcrypt;
    private final SystemConfigService configService;
    private final SmCryptoService crypto;
    private final SecureRandom random = new SecureRandom();
    private final String bcryptDummyHash;
    private final String sm3DummyHash;

    public CredentialPasswordService(PasswordEncoder bcrypt,
                                     SystemConfigService configService,
                                     SmCryptoService crypto) {
        this.bcrypt = bcrypt;
        this.configService = configService;
        this.crypto = crypto;
        this.bcryptDummyHash = bcrypt.encode(DUMMY_PASSWORD);
        this.sm3DummyHash = encodeSm3(DUMMY_PASSWORD, fixedDummySalt());
    }

    public String encode(String rawPassword) {
        if (configService.runtimeAuthPasswordHashAlgorithm() == AuthPasswordHashAlgorithm.SM3) {
            byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);
            return encodeSm3(rawPassword, salt);
        }
        return bcrypt.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return matchesDummy(rawPassword);
        }
        if (encoded.startsWith(SM3_PREFIX)) {
            return matchesSm3(rawPassword, encoded);
        }
        return bcrypt.matches(rawPassword, encoded);
    }

    public boolean matchesDummy(String rawPassword) {
        boolean bcryptResult = bcrypt.matches(rawPassword, bcryptDummyHash);
        boolean sm3Result = matchesSm3(rawPassword, sm3DummyHash);
        return bcryptResult && sm3Result;
    }

    public boolean isSm3Hash(String encoded) {
        return encoded != null && encoded.startsWith(SM3_PREFIX);
    }

    private String encodeSm3(String rawPassword, byte[] salt) {
        byte[] derived = derive(rawPassword, salt, SM3_ITERATIONS);
        return SM3_PREFIX
            + SM3_ITERATIONS
            + ":"
            + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
            + ":"
            + toHex(derived);
    }

    private boolean matchesSm3(String rawPassword, String encoded) {
        String[] parts = encoded.split(":");
        if (parts.length != 5 || !"sm3".equals(parts[0]) || !"v1".equals(parts[1])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[3]);
            byte[] expected = fromHex(parts[4]);
            byte[] actual = derive(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(String rawPassword, byte[] salt, int iterations) {
        if (iterations <= 0 || salt.length < SALT_BYTES) {
            throw new IllegalArgumentException("非法 SM3 口令哈希参数");
        }
        byte[] password = (rawPassword == null ? "" : rawPassword).getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[salt.length + password.length];
        System.arraycopy(salt, 0, input, 0, salt.length);
        System.arraycopy(password, 0, input, salt.length, password.length);
        byte[] current = input;
        for (int i = 0; i < iterations; i++) {
            current = crypto.sm3(current);
        }
        return current;
    }

    private byte[] fixedDummySalt() {
        return "mk-sm3-dummy-slt".getBytes(StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String text) {
        if (text == null || text.length() % 2 != 0) {
            throw new IllegalArgumentException("hex 长度非法");
        }
        byte[] result = new byte[text.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(text.charAt(i * 2), 16);
            int low = Character.digit(text.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("hex 字符非法");
            }
            result[i] = (byte) ((high << 4) + low);
        }
        return result;
    }
}
