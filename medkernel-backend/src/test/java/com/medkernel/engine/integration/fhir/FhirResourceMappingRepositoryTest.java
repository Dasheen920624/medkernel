package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.QualityStatus;
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
 * OPT-01 FHIR 资源映射表与规则表仓储测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:fhir-mapping-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class FhirResourceMappingRepositoryTest {

    @Autowired CanonicalResourceRepository canonicalResources;
    @Autowired FhirResourceMappingRepository mappings;
    @Autowired FhirMappingRuleRepository rules;

    @AfterEach
    void wipe() {
        rules.deleteAll();
        mappings.deleteAll();
        canonicalResources.deleteAll();
    }

    @Test
    void persistsResourceMappingAndFindsByBothDirections() {
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        CanonicalResource canonical = canonicalResources.save(new CanonicalResource(
            null,
            "obs-" + UUID.randomUUID(),
            "snapshot-1",
            "tenant-A",
            CanonicalResourceType.OBSERVATION,
            "{}",
            "FHIR_R4",
            "Observation/obs-1",
            "FHIR_R4:Observation",
            now,
            now,
            QualityStatus.VALID,
            0,
            "trace-fhir-map"));

        FhirResourceMapping saved = mappings.save(new FhirResourceMapping(
            null,
            "tenant-A",
            "/platform/group/hospital/dept",
            FhirVersion.R4,
            "Observation",
            "obs-1",
            canonical.id(),
            CanonicalResourceType.OBSERVATION,
            new BigDecimal("1.0000"),
            0,
            FhirMappingStatus.ACTIVE,
            "trace-fhir-map",
            now,
            "tester",
            now,
            "tester"));

        assertThat(saved.id()).isNotNull();
        assertThat(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Observation", "obs-1"))
            .hasValueSatisfying(row -> assertThat(row.canonicalResourceId()).isEqualTo(canonical.id()));
        assertThat(mappings.findByTenantIdAndCanonicalResourceIdAndFhirVersion(
            "tenant-A", canonical.id(), FhirVersion.R4))
            .hasValueSatisfying(row -> assertThat(row.fhirId()).isEqualTo("obs-1"));
    }

    @Test
    void persistsVersionedMappingRuleForR4Observation() {
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        rules.save(new FhirMappingRule(
            null,
            "tenant-A",
            "OBS_VALUE_QUANTITY_TO_CANONICAL_VALUE",
            FhirVersion.R4,
            "Observation",
            CanonicalResourceType.OBSERVATION,
            "valueQuantity.value",
            "valueNumeric",
            true,
            "COPY_NUMERIC",
            1,
            FhirMappingStatus.ACTIVE,
            "trace-rule",
            now,
            "tester",
            now,
            "tester"));

        List<FhirMappingRule> rows = rules
            .findByTenantIdAndFhirVersionAndFhirResourceTypeAndStatusOrderByRuleVersionDesc(
                "tenant-A", FhirVersion.R4, "Observation", FhirMappingStatus.ACTIVE);

        assertThat(rows).singleElement().satisfies(rule -> {
            assertThat(rule.ruleCode()).isEqualTo("OBS_VALUE_QUANTITY_TO_CANONICAL_VALUE");
            assertThat(rule.fhirPath()).isEqualTo("valueQuantity.value");
            assertThat(rule.canonicalPath()).isEqualTo("valueNumeric");
            assertThat(rule.requiredField()).isTrue();
            assertThat(rule.ruleVersion()).isEqualTo(1);
        });
    }
}
