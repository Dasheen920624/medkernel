package com.medkernel.engine.llm.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.medkernel.engine.datasvc.FieldEncryptionKeyResolver;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 模型服务凭据专用编解码器。
 *
 * <p>从字段加密主密钥派生独立用途的 SM4 密钥，与 D3/D4 字段加密上下文隔离。异常、返回值和
 * {@code toString()} 均不得包含凭据明文。
 */
@Component
public class ProviderCredentialCodec {

    private static final String PREFIX = "sm4:v1:";
    private static final String KEY_CONTEXT = "medkernel:llm:provider-credential:";
    private static final int SM4_KEY_BYTES = 16;

    private final SmCryptoService crypto;
    private final FieldEncryptionKeyResolver keyResolver;

    public ProviderCredentialCodec(
            SmCryptoService crypto,
            FieldEncryptionKeyResolver keyResolver) {
        this.crypto = crypto;
        this.keyResolver = keyResolver;
    }

    public EncodedCredential encode(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("模型凭据不能为空");
        }
        String normalized = plaintext.trim();
        try {
            byte[] cipher = crypto.sm4Encrypt(
                purposeKey(),
                normalized.getBytes(StandardCharsets.UTF_8)
            );
            return new EncodedCredential(
                PREFIX + crypto.base64Encode(cipher),
                sha256(normalized),
                normalized.substring(Math.max(0, normalized.length() - 4))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("模型凭据加密失败", exception);
        }
    }

    public String decode(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException("模型凭据密文格式无效");
        }
        try {
            byte[] cipher = crypto.base64Decode(ciphertext.substring(PREFIX.length()));
            return new String(crypto.sm4Decrypt(purposeKey(), cipher), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("模型凭据解密失败", exception);
        }
    }

    private byte[] purposeKey() {
        byte[] digest = crypto.sm3(
            (KEY_CONTEXT + keyResolver.resolve()).getBytes(StandardCharsets.UTF_8)
        );
        byte[] key = new byte[SM4_KEY_BYTES];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }

    private String sha256(String plaintext) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public record EncodedCredential(
        String ciphertext,
        String fingerprint,
        String last4
    ) {
        @Override
        public String toString() {
            return "EncodedCredential[last4=" + last4 + "]";
        }
    }
}
