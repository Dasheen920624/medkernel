package com.medkernel.engine.rule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

class RulePublicationStatusSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-07-05T08:00:00Z");

    private final RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versions = mock(RuleVersionRepository.class);
    private final RulePublicationStatusSynchronizer synchronizer =
        new RulePublicationStatusSynchronizer(definitions, versions);

    @Test
    void marksMatchingDraftRuleDefinitionAndVersionPublishedWhenUnifiedRuleVersionIsPublished() {
        RuleDefinition rule = rule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = versionProjection(RuleVersionStatus.DRAFT);
        when(definitions.findByTenantIdAndRuleCode("t-1", "RULE.LOCAL.REHEARSAL.BASELINE"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-local", "t-1", 1))
            .thenReturn(Optional.of(version));

        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.RULE, "RULE.LOCAL.REHEARSAL.BASELINE", "V1"),
            NOW,
            "engine-operator",
            "trace-rule-sync");

        verify(versions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.versionId().equals("rv-local")
                && value.status() == RuleVersionStatus.PUBLISHED
                && value.publishedAt().equals(NOW)
                && value.publishedBy().equals("engine-operator")
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("engine-operator")
                && value.traceId().equals("trace-rule-sync")));
        verify(definitions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.ruleId().equals("rule-local")
                && value.status() == RuleDefinitionStatus.PUBLISHED
                && value.activeVersionId().equals("rv-local")
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("engine-operator")
                && value.traceId().equals("trace-rule-sync")));
    }

    @Test
    void ignoresNonRulePublishedVersions() {
        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.FIELD_CATALOG, "FIELD.CATALOG.CLINICAL_CONTEXT", "V1"),
            NOW,
            "engine-operator",
            "trace-field");

        verifyNoInteractions(definitions, versions);
    }

    private AssetVersion assetVersion(
            VersionedAssetType assetType,
            String assetIdentity,
            String versionNo) {
        return new AssetVersion(
            1L,
            "av-" + assetIdentity,
            "t-1",
            assetType,
            assetIdentity,
            versionNo,
            null,
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:av-" + assetIdentity,
            "local-e2e",
            NOW,
            null,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW,
            "engine-operator",
            "trace-rule-sync"
        );
    }

    private RuleDefinition rule(RuleDefinitionStatus status) {
        return new RuleDefinition(
            1L,
            "rule-local",
            "t-1",
            "RULE.LOCAL.REHEARSAL.BASELINE",
            "本地上线演练平台基础规则",
            RuleType.QUALITY,
            RuleAuthoringMode.DSL,
            RuleRiskLevel.LOW,
            100,
            null,
            0,
            status,
            "rv-local",
            null,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW.minusSeconds(3600),
            "engine-operator",
            "trace-old"
        );
    }

    private RuleVersion versionProjection(RuleVersionStatus status) {
        return new RuleVersion(
            1L,
            "rv-local",
            "t-1",
            "rule-local",
            1,
            "local-e2e",
            "清库上线演练自动准备平台基础运行规则",
            "{}",
            "{}",
            status,
            null,
            null,
            null,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW.minusSeconds(3600),
            "engine-operator",
            "trace-old"
        );
    }
}
