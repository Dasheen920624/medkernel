package com.medkernel.engine.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.medkernel.shared.crypto.SmCryptoService;

/**
 * Webhook 共享密钥编解码器。
 *
 * <p>共享密钥只在创建响应中展示一次，数据库仅保存带版本前缀的 SM4 密文。
 */
@Component
public class WebhookSecretCodec {

    private static final String PREFIX = "sm4:v1:";
    private static final int SECRET_BYTES = 32;
    private static final int SM4_KEY_BYTES = 16;

    private final SmCryptoService crypto;
    private final IntegrationSecretKeyResolver keyResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSecretCodec(SmCryptoService crypto, IntegrationSecretKeyResolver keyResolver) {
        this.crypto = crypto;
        this.keyResolver = keyResolver;
    }

    public String generateSecret() {
        byte[] random = new byte[SECRET_BYTES];
        secureRandom.nextBytes(random);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String encode(String secret) {
        try {
            byte[] cipher = crypto.sm4Encrypt(sm4Key(), secret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + crypto.base64Encode(cipher);
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook 共享密钥加密失败", exception);
        }
    }

    public String decode(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            throw new IllegalStateException("Webhook 共享密钥密文格式无效");
        }
        try {
            byte[] cipher = crypto.base64Decode(encoded.substring(PREFIX.length()));
            byte[] plain = crypto.sm4Decrypt(sm4Key(), cipher);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook 共享密钥解密失败", exception);
        }
    }

    private byte[] sm4Key() {
        byte[] digest = crypto.sm3(
            ("medkernel:integration:webhook:" + keyResolver.resolve()).getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[SM4_KEY_BYTES];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }
}
