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

/**
 * 模型服务配置仓储回归测试（LLM-08，V125 五方言迁移）。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:model-provider-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ModelProviderConfigRepositoryTest {

    @Autowired
    ModelProviderConfigRepository repository;

    @Test
    void savesAndQueriesByTenantAndProviderCode() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "ollama-local", "OLLAMA",
            "http://127.0.0.1:11434", "qwen2.5:7b", "Y", "HEALTHY",
            now, "system", now, "system", null));

        assertThat(repository.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .isPresent()
            .get()
            .satisfies(p -> {
                assertThat(p.providerType()).isEqualTo("OLLAMA");
                assertThat(p.endpointUri()).isEqualTo("http://127.0.0.1:11434");
            });
    }

    @Test
    void listsEnabledProvidersForTenant() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "ollama-local", "OLLAMA",
            "http://127.0.0.1:11434", "qwen2.5:7b", "Y", "HEALTHY",
            now, "system", now, "system", null));
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "claude-prod", "CLAUDE",
            "https://api.anthropic.com", "claude-opus-4-8", "N", "NOT_CONNECTED",
            now, "system", now, "system", null));

        assertThat(repository.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .hasSize(1)
            .first()
            .extracting(ModelProviderConfig::providerCode)
            .isEqualTo("ollama-local");
    }

    @Test
    void countsAndPagesProvidersWithinTenantOnly() {
        Instant older = Instant.parse("2026-06-14T00:00:00Z");
        Instant newer = Instant.parse("2026-06-14T00:01:00Z");
        repository.save(provider("tenant-1", "older", older));
        repository.save(provider("tenant-1", "newer", newer));
        repository.save(provider("tenant-2", "other-tenant", newer));

        assertThat(repository.countByTenantId("tenant-1")).isEqualTo(2);
        assertThat(repository.pageByTenantId("tenant-1", 0, 1))
            .singleElement()
            .extracting(ModelProviderConfig::providerCode)
            .isEqualTo("newer");
        assertThat(repository.pageByTenantId("tenant-1", 1, 1))
            .singleElement()
            .extracting(ModelProviderConfig::providerCode)
            .isEqualTo("older");
    }

    @Test
    void rejectsStaleProviderSnapshotWithOptimisticLock() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "ollama-local", "OLLAMA",
            "http://127.0.0.1:11434", "qwen2.5:7b", "N", "NOT_CONNECTED",
            now, "system", now, "system", null));

        ModelProviderConfig first = repository
            .findByTenantIdAndProviderCode("tenant-1", "ollama-local")
            .orElseThrow();
        ModelProviderConfig stale = repository
            .findByTenantIdAndProviderCode("tenant-1", "ollama-local")
            .orElseThrow();

        ModelProviderConfig updated = repository.save(withStatus(first, "HEALTHY"));

        assertThat(updated.version()).isGreaterThan(first.version());
        assertThatThrownBy(() -> repository.save(withStatus(stale, "NOT_CONNECTED")))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private static ModelProviderConfig withStatus(
            ModelProviderConfig config,
            String status) {
        return new ModelProviderConfig(
            config.id(),
            config.tenantId(),
            config.providerCode(),
            config.providerType(),
            config.endpointUri(),
            config.modelVersion(),
            config.enabledFlag(),
            status,
            config.createdAt(),
            config.createdBy(),
            Instant.parse("2026-06-14T00:01:00Z"),
            "system",
            config.version());
    }

    private static ModelProviderConfig provider(
            String tenantId,
            String providerCode,
            Instant updatedAt) {
        return new ModelProviderConfig(
            null,
            tenantId,
            providerCode,
            "OLLAMA",
            "http://127.0.0.1:11434",
            "qwen2.5:7b",
            "N",
            "NOT_CONNECTED",
            updatedAt,
            "system",
            updatedAt,
            "system",
            null);
    }
}
