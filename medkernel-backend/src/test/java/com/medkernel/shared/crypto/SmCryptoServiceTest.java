package com.medkernel.shared.crypto;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GA-CORE-02 / W1-G5：国密 SM2 / SM3 / SM4 单元测试。
 */
class SmCryptoServiceTest {

    private SmCryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new SmCryptoService();
        crypto.init();
    }

    @Test
    void sm3KnownValue() {
        // GB/T 32905-2016 标准测试向量 "abc"
        String hex = crypto.sm3Hex("abc");
        assertThat(hex).isEqualTo("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
    }

    @Test
    void sm3StreamMatchesKnownValue() throws Exception {
        String hex = crypto.sm3Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

        assertThat(hex).isEqualTo("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
    }

    @Test
    void sm4RoundTrip() throws Exception {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] plain = "MedKernel v1.0 GA 国密测试 — 患者敏感数据".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = crypto.sm4Encrypt(key, plain);
        byte[] decrypted = crypto.sm4Decrypt(key, cipher);

        assertThat(decrypted).isEqualTo(plain);
        assertThat(cipher).isNotEqualTo(plain);
    }

    @Test
    void sm2RoundTrip() throws Exception {
        KeyPair kp = crypto.generateSm2KeyPair();
        byte[] plain = "MedKernel v1.0 GA SM2 测试 — 临床决策数据".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = crypto.sm2Encrypt(kp.getPublic(), plain);
        byte[] decrypted = crypto.sm2Decrypt(kp.getPrivate(), cipher);

        assertThat(decrypted).isEqualTo(plain);
        assertThat(cipher).isNotEqualTo(plain);
    }

    @Test
    void base64Tools() {
        byte[] raw = new byte[]{1, 2, 3, 4, 5};
        String encoded = crypto.base64Encode(raw);
        byte[] decoded = crypto.base64Decode(encoded);
        assertThat(decoded).isEqualTo(raw);
    }
}
