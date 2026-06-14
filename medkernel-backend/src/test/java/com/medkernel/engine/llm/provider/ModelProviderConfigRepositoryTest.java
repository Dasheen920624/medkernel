package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * 模型 provider 配置仓储回归测试（LLM-08，V125 五方言迁移）。
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
            "http://127.0.0.1:11434", null, "qwen2.5:7b", "Y", "HEALTHY",
            now, "system", now, "system"));

        assertThat(repository.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .isPresent()
            .get()
            .satisfies(p -> {
                assertThat(p.providerType()).isEqualTo("OLLAMA");
                assertThat(p.endpointUri()).isEqualTo("http://127.0.0.1:11434");
                assertThat(p.credentialRef()).isNull();
            });
    }

    @Test
    void listsEnabledProvidersForTenant() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "ollama-local", "OLLAMA",
            "http://127.0.0.1:11434", null, "qwen2.5:7b", "Y", "HEALTHY",
            now, "system", now, "system"));
        repository.save(new ModelProviderConfig(
            null, "tenant-1", "claude-prod", "CLAUDE",
            "https://api.anthropic.com", "cred-ref-1", "claude-opus-4-8", "N", "NOT_CONNECTED",
            now, "system", now, "system"));

        assertThat(repository.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .hasSize(1)
            .first()
            .extracting(ModelProviderConfig::providerCode)
            .isEqualTo("ollama-local");
    }
}
