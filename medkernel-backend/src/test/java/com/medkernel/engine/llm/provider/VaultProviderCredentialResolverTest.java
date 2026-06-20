package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void resolvesTenantVaultCredential() {
        ModelProviderCredential row = credential("tenant-a", "provider-a", "sm4:v1:cipher-a");
        when(repository.findByTenantIdAndProviderCode("tenant-a", "provider-a"))
            .thenReturn(Optional.of(row));
        when(codec.decode("sm4:v1:cipher-a")).thenReturn("vault-secret");
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec);

        Optional<String> secret = resolver.resolveSecret("tenant-a", "provider-a");

        assertThat(secret).contains("vault-secret");
    }

    @Test
    void returnsEmptyWhenTenantVaultHasNoCredential() {
        when(repository.findByTenantIdAndProviderCode("tenant-a", "provider-a"))
            .thenReturn(Optional.empty());
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec);

        Optional<String> secret = resolver.resolveSecret("tenant-a", "provider-a");

        assertThat(secret).isEmpty();
    }

    @Test
    void neverReadsAnotherTenantsCredential() {
        when(repository.findByTenantIdAndProviderCode("tenant-b", "provider-a"))
            .thenReturn(Optional.empty());
        VaultProviderCredentialResolver resolver =
            new VaultProviderCredentialResolver(repository, codec);

        Optional<String> secret = resolver.resolveSecret("tenant-b", "provider-a");

        assertThat(secret).isEmpty();
        verify(repository).findByTenantIdAndProviderCode("tenant-b", "provider-a");
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
