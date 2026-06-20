package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VaultProviderCredentialResolverTest {

    @Mock
    private ModelProviderCredentialRepository repository;

    @Mock
    private ProviderCredentialCodec codec;

    @Mock
    private EnvProviderCredentialResolver environment;

    @Test
    void tenantVaultWinsOverEnvironmentReference() {
        ModelProviderCredential row = credential("tenant-a", "provider-a", "sm4:v1:cipher-a");
        when(repository.findByTenantIdAndProviderCode("tenant-a", "provider-a"))
            .thenReturn(Optional.of(row));
        when(codec.decode("sm4:v1:cipher-a")).thenReturn("vault-secret");
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec, environment);

        Optional<String> secret = resolver.resolveSecret("tenant-a", "provider-a", "MODEL_API_KEY");

        assertThat(secret).contains("vault-secret");
        verify(environment, never()).resolveSecret("MODEL_API_KEY");
    }

    @Test
    void fallsBackToEnvironmentWhenTenantVaultHasNoCredential() {
        when(repository.findByTenantIdAndProviderCode("tenant-a", "provider-a"))
            .thenReturn(Optional.empty());
        when(environment.resolveSecret("MODEL_API_KEY")).thenReturn(Optional.of("environment-secret"));
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec, environment);

        Optional<String> secret = resolver.resolveSecret("tenant-a", "provider-a", "MODEL_API_KEY");

        assertThat(secret).contains("environment-secret");
    }

    @Test
    void neverReadsAnotherTenantsCredential() {
        when(repository.findByTenantIdAndProviderCode("tenant-b", "provider-a"))
            .thenReturn(Optional.empty());
        when(environment.resolveSecret(null)).thenReturn(Optional.empty());
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec, environment);

        Optional<String> secret = resolver.resolveSecret("tenant-b", "provider-a", null);

        assertThat(secret).isEmpty();
        verify(repository).findByTenantIdAndProviderCode("tenant-b", "provider-a");
        verify(repository, never()).findByTenantIdAndProviderCode("tenant-a", "provider-a");
    }

    private ModelProviderCredential credential(String tenantId, String providerCode, String ciphertext) {
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        return new ModelProviderCredential(
            1L,
            tenantId,
            providerCode,
            ciphertext,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "1234",
            now,
            "operator",
            now,
            "operator",
            "trace-1",
            0L
        );
    }
}
