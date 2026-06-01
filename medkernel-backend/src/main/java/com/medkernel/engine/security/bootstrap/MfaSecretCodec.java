package com.medkernel.engine.security.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.security.JwtSecretResolver;

/**
 * MFA secret 存储编解码：TOTP secret 使用 SM4 加密，恢复码只保存 SM3 摘要。
 */
@Component
public class MfaSecretCodec {

    private static final String PREFIX = "totp:v1:";
    private static final int SM4_KEY_BYTES = 16;

    private final SmCryptoService crypto;
    private final JwtSecretResolver secretResolver;

    public MfaSecretCodec(SmCryptoService crypto, JwtSecretResolver secretResolver) {
        this.crypto = crypto;
        this.secretResolver = secretResolver;
    }

    public String encode(String totpSecret, String recoveryCode) {
        try {
            String encryptedSecret = crypto.base64Encode(
                crypto.sm4Encrypt(sm4Key(), totpSecret.getBytes(StandardCharsets.UTF_8)));
            return PREFIX + encryptedSecret + ":" + recoveryHash(recoveryCode);
        } catch (Exception ex) {
            throw new IllegalStateException("MFA secret 加密失败", ex);
        }
    }

    public Optional<String> decodeTotpSecret(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = encoded.split(":", 4);
        if (parts.length != 4 || parts[2].isBlank() || parts[3].isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] plain = crypto.sm4Decrypt(sm4Key(), crypto.base64Decode(parts[2]));
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public boolean isTotpBound(String encoded) {
        return decodeTotpSecret(encoded).isPresent();
    }

    private String recoveryHash(String recoveryCode) {
        return "sm3:" + crypto.sm3Hex(recoveryCode == null ? "" : recoveryCode);
    }

    private byte[] sm4Key() {
        byte[] digest = crypto.sm3(secretResolver.resolve().getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[SM4_KEY_BYTES];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }
}
