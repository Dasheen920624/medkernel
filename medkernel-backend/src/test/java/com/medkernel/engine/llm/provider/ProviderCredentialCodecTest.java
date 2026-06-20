package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.datasvc.FieldEncryptionKeyResolver;
import com.medkernel.shared.crypto.SmCryptoService;

class ProviderCredentialCodecTest {

    private static final String MASTER_KEY =
        "provider-credential-test-master-key-at-least-thirty-two-bytes";

    private final SmCryptoService crypto = new SmCryptoService();
    private final FieldEncryptionKeyResolver keyResolver = () -> MASTER_KEY;
    private final ProviderCredentialCodec codec = new ProviderCredentialCodec(crypto, keyResolver);

    @Test
    void encryptsDecryptsAndReturnsOnlySafeMetadata() {
        String plaintext = "sk-live-very-sensitive-1234";

        ProviderCredentialCodec.EncodedCredential encoded = codec.encode(plaintext);

        assertThat(encoded.ciphertext())
            .startsWith("sm4:v1:")
            .doesNotContain(plaintext);
        assertThat(encoded.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(encoded.last4()).isEqualTo("1234");
        assertThat(encoded.toString())
            .contains("last4=1234")
            .doesNotContain(plaintext, encoded.ciphertext(), encoded.fingerprint());
        assertThat(codec.decode(encoded.ciphertext())).isEqualTo(plaintext);
    }

    @Test
    void providerCiphertextCannotBeDecryptedWithDataServicePurposeKey() throws Exception {
        ProviderCredentialCodec.EncodedCredential encoded = codec.encode("sk-purpose-separated-5678");
        byte[] dataServiceKey = first16(crypto.sm3(
            ("medkernel:datasvc:field-encryption:" + MASTER_KEY).getBytes(StandardCharsets.UTF_8)));
        byte[] ciphertext = crypto.base64Decode(encoded.ciphertext().substring("sm4:v1:".length()));

        assertThatThrownBy(() -> crypto.sm4Decrypt(dataServiceKey, ciphertext))
            .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsBlankOrMalformedCredentialMaterialWithoutEchoingIt() {
        assertThatThrownBy(() -> codec.encode(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("模型凭据不能为空");

        assertThatThrownBy(() -> codec.decode("plain:secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("模型凭据密文格式无效")
            .hasMessageNotContaining("plain:secret");
    }

    private byte[] first16(byte[] digest) {
        byte[] key = new byte[16];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }
}
