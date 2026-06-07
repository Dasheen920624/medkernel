package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:model-capability-catalog-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ModelCapabilityDefinitionRepositoryTest {

    @Autowired
    ModelCapabilityDefinitionRepository repository;

    @Test
    void migratedCatalogLoadsInStableOrderAndSupportsUpdates() {
        assertThat(repository.findAllByOrderBySortOrderAscCapabilityCodeAsc())
            .hasSize(8)
            .first()
            .extracting(ModelCapabilityDefinition::capabilityCode)
            .isEqualTo("knowledge.discovery");

        ModelCapabilityDefinition existing = repository.findById("knowledge.extract").orElseThrow();
        Instant updatedAt = existing.updatedAt().plusSeconds(60);
        repository.save(new ModelCapabilityDefinition(
            existing.capabilityCode(),
            "结构化病历提取",
            existing.description(),
            existing.category(),
            "N",
            15,
            existing.createdAt(),
            existing.createdBy(),
            updatedAt,
            "tester"
        ));

        assertThat(repository.findById("knowledge.extract"))
            .get()
            .satisfies(saved -> {
                assertThat(saved.displayName()).isEqualTo("结构化病历提取");
                assertThat(saved.enabled()).isFalse();
                assertThat(saved.updatedBy()).isEqualTo("tester");
            });
    }
}
