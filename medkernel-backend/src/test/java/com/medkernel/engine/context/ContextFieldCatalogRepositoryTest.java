package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

/**
 * 租户自定义上下文字段仓储测试（P2/P5）：V72 建表后可持久化与按租户/状态反查。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:ctx-field-catalog-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ContextFieldCatalogRepositoryTest {

    @Autowired ContextFieldCatalogRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    private ContextFieldCatalogEntry sample(String tenantId, String fieldPath, String status) {
        Instant now = Instant.now();
        return new ContextFieldCatalogEntry(
            null, UUID.randomUUID().toString(), tenantId, "医嘱信息", "用药医嘱", "Medication",
            fieldPath, "院内自定义字段", "string", null, null, "院内扩展", status,
            now, "tester", now, "tester", "trace-1");
    }

    @Test
    void persistsAndQueriesByTenantAndStatus() {
        repository.save(sample("tenant-A", "medications[].customFlag", "ACTIVE"));
        repository.save(sample("tenant-A", "medications[].deprecatedFlag", "DEPRECATED"));
        repository.save(sample("tenant-B", "medications[].otherFlag", "ACTIVE"));

        List<ContextFieldCatalogEntry> active =
            repository.findAllByTenantIdAndStatus("tenant-A", "ACTIVE");
        assertThat(active).hasSize(1);
        assertThat(active.get(0).fieldPath()).isEqualTo("medications[].customFlag");
        assertThat(active.get(0).category()).isEqualTo("医嘱信息");
        assertThat(active.get(0).groupName()).isEqualTo("用药医嘱");

        assertThat(repository.findByTenantIdAndFieldPath("tenant-A", "medications[].customFlag"))
            .isPresent();
        // 跨租户隔离
        assertThat(repository.findAllByTenantIdAndStatus("tenant-B", "ACTIVE")).hasSize(1);
    }

    @Test
    void toDescriptorMapsBusinessHierarchy() {
        ContextFieldDescriptor d = sample("t", "x.y", "ACTIVE").toDescriptor();
        assertThat(d.category()).isEqualTo("医嘱信息");
        assertThat(d.group()).isEqualTo("用药医嘱");
        assertThat(d.fieldPath()).isEqualTo("x.y");
    }
}
