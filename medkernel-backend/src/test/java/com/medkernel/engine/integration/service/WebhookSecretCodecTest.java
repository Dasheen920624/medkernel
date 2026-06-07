package com.medkernel.engine.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.medkernel.shared.crypto.SmCryptoService;

class WebhookSecretCodecTest {

    @Test
    void generatedSecretIsEncryptedAtRestAndCanBeDecoded() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("MEDKERNEL_INTEGRATION_SECRET_KEY"))
            .thenReturn("integration-test-master-key-at-least-32-bytes");
        WebhookSecretCodec codec = new WebhookSecretCodec(
            new SmCryptoService(),
            new IntegrationSecretKeyResolver(environment)
        );

        String secret = codec.generateSecret();
        String cipher = codec.encode(secret);

        assertTrue(secret.startsWith("whsec_"));
        assertTrue(cipher.startsWith("sm4:v1:"));
        assertFalse(cipher.contains(secret));
        assertEquals(secret, codec.decode(cipher));
    }
}
