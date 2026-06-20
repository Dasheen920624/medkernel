package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:model-provider-credential-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ModelProviderCredentialRepositoryTest {

    @Autowired
    private ModelProviderCredentialRepository repository;

    @Test
    void persistsCiphertextMetadataAndKeepsTenantIsolation() {
        repository.save(credential(null, "tenant-a", "provider-a", "sm4:v1:cipher-a", null));

        assertThat(repository.findByTenantIdAndProviderCode("tenant-a", "provider-a"))
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.credentialCiphertext()).isEqualTo("sm4:v1:cipher-a");
                assertThat(saved.credentialFingerprint()).hasSize(64);
                assertThat(saved.credentialLast4()).isEqualTo("1234");
                assertThat(saved.version()).isZero();
                assertThat(saved.toString())
                    .contains("last4=1234")
                    .doesNotContain(saved.credentialCiphertext(), saved.credentialFingerprint());
            });
        assertThat(repository.findByTenantIdAndProviderCode("tenant-b", "provider-a")).isEmpty();
    }

    @Test
    void rejectsStaleCredentialRotationWithOptimisticLock() {
        repository.save(credential(null, "tenant-a", "provider-a", "sm4:v1:cipher-a", null));
        ModelProviderCredential current = repository
            .findByTenantIdAndProviderCode("tenant-a", "provider-a")
            .orElseThrow();
        ModelProviderCredential stale = repository
            .findByTenantIdAndProviderCode("tenant-a", "provider-a")
            .orElseThrow();

        ModelProviderCredential rotated = repository.save(
            credential(current.id(), "tenant-a", "provider-a", "sm4:v1:cipher-b", current.version())
        );

        assertThat(rotated.version()).isGreaterThan(current.version());
        assertThatThrownBy(() -> repository.save(
            credential(stale.id(), "tenant-a", "provider-a", "sm4:v1:cipher-c", stale.version())
        )).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private ModelProviderCredential credential(
            Long id,
            String tenantId,
            String providerCode,
            String ciphertext,
            Long version) {
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        return new ModelProviderCredential(
            id,
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
            version
        );
    }
}
