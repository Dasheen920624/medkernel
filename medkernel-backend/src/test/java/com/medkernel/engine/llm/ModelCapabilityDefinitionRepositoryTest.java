package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

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
@Import(ModelCatalogSeeder.class)
class ModelCapabilityDefinitionRepositoryTest {

    @Autowired
    ModelCapabilityDefinitionRepository repository;

    @Autowired
    ModelCatalogSeeder seeder;

    @BeforeEach
    void seedCatalog() {
        seeder.seed();
    }

    @Test
    void save_insertsBrandNewCapabilityCode() {
        // 回归 2026-06-10 首次部署缺陷族：自然键主键新建须显式声明新建语义，否则被误判为 UPDATE。
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        ModelCapabilityDefinition fresh = new ModelCapabilityDefinition(
            "knowledge.regression-test", "回归测试能力", "首次插入回归用例", "knowledge",
            "Y", 99, now, "tester", now, "tester", true);

        repository.save(fresh);

        assertThat(repository.findById("knowledge.regression-test"))
            .isPresent()
            .get()
            .extracting(ModelCapabilityDefinition::displayName)
            .isEqualTo("回归测试能力");
    }

    @Test
    void applicationCatalogLoadsInStableOrderAndSupportsUpdates() {
        assertThat(repository.findAllByOrderBySortOrderAscCapabilityCodeAsc())
            .hasSize(9)
            .first()
            .extracting(ModelCapabilityDefinition::capabilityCode)
            .isEqualTo("knowledge.discovery");
        assertThat(repository.findById("knowledge.production.knowledge"))
            .isPresent();

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

    @Test
    void repeatedSeedDoesNotOverwriteOperatorChanges() {
        ModelCapabilityDefinition existing = repository.findById("knowledge.extract").orElseThrow();
        repository.save(new ModelCapabilityDefinition(
            existing.capabilityCode(), existing.displayName(), existing.description(), existing.category(),
            "N", existing.sortOrder(), existing.createdAt(), existing.createdBy(),
            existing.updatedAt().plusSeconds(1), "operator"));

        seeder.seed();

        assertThat(repository.findById("knowledge.extract")).get()
            .satisfies(saved -> {
                assertThat(saved.enabled()).isFalse();
                assertThat(saved.updatedBy()).isEqualTo("operator");
            });
    }
}
