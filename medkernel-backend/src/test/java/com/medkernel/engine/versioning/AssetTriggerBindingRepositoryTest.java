package com.medkernel.engine.versioning;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 资产多触发绑定仓储契约。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:asset-trigger-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AssetTriggerBindingRepositoryTest {

    @Autowired AssetVersionRepository versions;
    @Autowired AssetTriggerBindingRepository bindings;

    @AfterEach
    void wipe() {
        bindings.deleteAll();
        versions.deleteAll();
    }

    @Test
    void oneRuleVersionCanBindMultipleExecutionTriggerPoints() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        versions.save(version("rule-v2", VersionedAssetType.RULE, "RULE.CKD", now));

        bindings.save(binding(
            "trigger-1", "rule-v2", VersionedAssetType.RULE, "RULE.CKD",
            "ORDER_SIGN", AssetTriggerPurpose.RULE_EXECUTION,
            "[\"patient.diagnoses\",\"order.code\"]", now));
        bindings.save(binding(
            "trigger-2", "rule-v2", VersionedAssetType.RULE, "RULE.CKD",
            "RESULT_REVIEW", AssetTriggerPurpose.RULE_EXECUTION,
            "[\"patient.diagnoses\",\"result.value\"]", now));

        assertThat(bindings
            .findByTenantIdAndVersionIdAndPurposeOrderByTriggerPointAsc(
                "tenant-A", "rule-v2", AssetTriggerPurpose.RULE_EXECUTION))
            .extracting(AssetTriggerBinding::triggerPoint)
            .containsExactly("ORDER_SIGN", "RESULT_REVIEW");
    }

    @Test
    void sameVersionTriggerPointAndPurposeCannotRepeat() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        versions.save(version("path-v3", VersionedAssetType.PATHWAY, "PATH.CKD", now));
        AssetTriggerBinding first = binding(
            "trigger-1", "path-v3", VersionedAssetType.PATHWAY, "PATH.CKD",
            "DIAGNOSIS_CONFIRMED", AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE,
            "[\"patient.diagnoses\"]", now);
        bindings.save(first);

        assertThatThrownBy(() -> bindings.save(binding(
            "trigger-2", "path-v3", VersionedAssetType.PATHWAY, "PATH.CKD",
            "DIAGNOSIS_CONFIRMED", AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE,
            "[\"patient.diagnoses\"]", now)))
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void ruleAndPathwayPurposesAreClosedByDatabaseConstraint() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        versions.save(version("rule-v2", VersionedAssetType.RULE, "RULE.CKD", now));

        assertThatThrownBy(() -> bindings.save(binding(
            "trigger-invalid", "rule-v2", VersionedAssetType.RULE, "RULE.CKD",
            "ORDER_SIGN", AssetTriggerPurpose.PATHWAY_PROGRESS, "[]", now)))
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    private AssetVersion version(
            String versionId,
            VersionedAssetType assetType,
            String assetIdentity,
            Instant now) {
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            assetType,
            assetIdentity,
            "V2",
            "tenant:tenant-A",
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:" + versionId,
            "权威来源",
            null,
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        );
    }

    private AssetTriggerBinding binding(
            String bindingId,
            String versionId,
            VersionedAssetType assetType,
            String assetIdentity,
            String triggerPoint,
            AssetTriggerPurpose purpose,
            String requiredFieldsJson,
            Instant now) {
        return new AssetTriggerBinding(
            null,
            bindingId,
            "tenant-A",
            assetType,
            assetIdentity,
            versionId,
            triggerPoint,
            purpose,
            requiredFieldsJson,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        );
    }
}
