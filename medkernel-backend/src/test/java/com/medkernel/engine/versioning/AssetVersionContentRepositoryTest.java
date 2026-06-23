package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
 * 统一资产正文仓储测试：V1 基线必须能持久化并按租户和版本精确读取完整正文。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:asset-version-content-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AssetVersionContentRepositoryTest {

    @Autowired AssetVersionRepository versions;
    @Autowired AssetVersionContentRepository contents;

    @AfterEach
    void wipe() {
        contents.deleteAll();
        versions.deleteAll();
    }

    @Test
    void persistsRecoverableBodyForExactVersion() {
        Instant now = Instant.parse("2026-06-22T08:00:00Z");
        AssetVersion version = versions.save(new AssetVersion(
            null,
            "av-field-catalog-1",
            "tenant-A",
            VersionedAssetType.FIELD_CATALOG,
            "FIELD.CANONICAL",
            "1.0.0",
            "tenant:tenant-A",
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:av-field-catalog-1",
            "canonical-field-catalog",
            null,
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));
        contents.save(new AssetVersionContent(
            null,
            version.versionId(),
            version.tenantId(),
            "{\"schemaVersion\":\"1.0\",\"fields\":[]}",
            version.contentHash(),
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));

        assertThat(contents.findByTenantIdAndVersionId("tenant-A", "av-field-catalog-1"))
            .get()
            .satisfies(content -> {
                assertThat(content.contentJson()).contains("\"fields\"");
                assertThat(content.contentHash()).isEqualTo(version.contentHash());
            });
        assertThat(contents.findByTenantIdAndVersionId("tenant-B", "av-field-catalog-1"))
            .isEmpty();
    }
}
