package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.versioning.PlatformAuthority;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:standard-term-platform-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class StandardTermRepositoryIntegrationTest {

    @Autowired
    StandardTermRepository repository;

    @Autowired
    LocalTermRepository localTerms;

    @Autowired
    TermMappingRepository mappings;

    @Test
    void tenantStandardQueryIncludesPlatformBaselineAndTenantOverride() {
        Instant now = Instant.parse("2026-06-06T04:30:00Z");
        StandardTerm platform = repository.save(term(
            PlatformAuthority.PLATFORM_TENANT_ID, "6598-7", "肌钙蛋白I 平台", now.minusSeconds(60)));
        StandardTerm tenant = repository.save(term(
            "tenant-A", "6598-7", "肌钙蛋白I 院内覆盖", now));

        List<String> sources = List.of(PlatformAuthority.PLATFORM_TENANT_ID, "tenant-A");

        assertThat(repository.countByTenantIdsFilter(sources, "LOINC", "LAB", "ACTIVE", "%肌钙蛋白%"))
            .isEqualTo(2);
        assertThat(repository.pageByTenantIdsFilter(
                sources, "tenant-A", "LOINC", "LAB", "ACTIVE", "%肌钙蛋白%", 0, 10))
            .extracting(StandardTerm::id)
            .containsExactly(tenant.id(), platform.id());
        assertThat(repository.findByTenantIdsAndStatus(sources, "tenant-A", StandardTermStatus.ACTIVE))
            .extracting(StandardTerm::tenantId)
            .containsExactly("tenant-A", PlatformAuthority.PLATFORM_TENANT_ID);
        assertThat(repository.findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
                sources, "tenant-A", "LOINC", "6598-7", StandardTermStatus.ACTIVE))
            .map(StandardTerm::id)
            .contains(tenant.id());
        assertThat(repository.findFirstByTenantIdsAndId(sources, "tenant-A", platform.id()))
            .map(StandardTerm::tenantId)
            .contains(PlatformAuthority.PLATFORM_TENANT_ID);
    }

    @Test
    void persistedTenantMappingCanReferencePlatformStandardTerm() {
        Instant now = Instant.parse("2026-06-06T05:00:00Z");
        StandardTerm platform = repository.save(term(
            PlatformAuthority.PLATFORM_TENANT_ID, "718-7", "血红蛋白 平台", now.minusSeconds(60)));
        LocalTerm local = localTerms.save(new LocalTerm(
            null,
            "tenant-A",
            "LIS",
            "HB",
            TermCategory.LAB,
            "血红蛋白",
            "血红蛋白",
            "CARD",
            LocalTermStatus.MAPPED,
            now,
            now,
            now,
            "system",
            now,
            "system"
        ));
        TermMapping mapping = mappings.save(new TermMapping(
            null,
            "tenant-A",
            local.id(),
            platform.id(),
            "LIS",
            TermCategory.LAB,
            1.0D,
            TermRiskLevel.LOW,
            TermMappingStatus.CONFIRMED,
            "人工确认 LIS:HB -> LOINC:718-7",
            "tester",
            now,
            now,
            "tester",
            now,
            "tester"
        ));

        assertThat(mappings.findByTenantIdAndId("tenant-A", mapping.id()))
            .map(TermMapping::standardTermId)
            .contains(platform.id());
    }

    private StandardTerm term(String tenantId, String code, String name, Instant updatedAt) {
        return new StandardTerm(
            null,
            tenantId,
            "LOINC",
            code,
            TermCategory.LAB,
            name,
            name.toLowerCase(),
            "2026.06",
            StandardTermStatus.ACTIVE,
            null,
            "平台标准术语集",
            updatedAt.minusSeconds(300),
            "system",
            updatedAt,
            "system"
        );
    }
}
