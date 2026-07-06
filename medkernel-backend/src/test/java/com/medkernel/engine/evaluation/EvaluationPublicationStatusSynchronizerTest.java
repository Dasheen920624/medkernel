package com.medkernel.engine.evaluation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

class EvaluationPublicationStatusSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-07-06T10:20:00Z");

    private final EvaluationIndicatorRepository indicators = mock(EvaluationIndicatorRepository.class);
    private final EvaluationPublicationStatusSynchronizer synchronizer =
        new EvaluationPublicationStatusSynchronizer(indicators);

    @Test
    void activatesMatchingDraftIndicatorWhenUnifiedEvaluationVersionIsPublished() {
        EvaluationIndicator draft = indicator(
            "ei-baseline-v1", 1, EvaluationIndicatorStatus.DRAFT, null, null);
        EvaluationIndicator oldActive = indicator(
            "ei-baseline-v0", 0, EvaluationIndicatorStatus.ACTIVE, NOW.minusSeconds(7200), NOW.minusSeconds(7200));
        when(indicators.findByTenantIdAndIndicatorCodeAndVersionNo(
            "t-1", "EVAL.LOCAL.REHEARSAL.BASELINE", 1))
            .thenReturn(Optional.of(draft));
        when(indicators.findByTenantIdAndIndicatorCodeAndStatus(
            "t-1", "EVAL.LOCAL.REHEARSAL.BASELINE", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(oldActive));

        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.EVALUATION, "EVAL.LOCAL.REHEARSAL.BASELINE", "V1"),
            NOW,
            "engine-operator",
            "trace-eval-sync");

        verify(indicators).save(argThat(value ->
            value.indicatorId().equals("ei-baseline-v0")
                && value.status() == EvaluationIndicatorStatus.OFFLINE
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("engine-operator")
                && value.traceId().equals("trace-eval-sync")));
        verify(indicators).save(argThat(value ->
            value.indicatorId().equals("ei-baseline-v1")
                && value.status() == EvaluationIndicatorStatus.ACTIVE
                && value.publishedAt().equals(NOW)
                && value.publishedBy().equals("engine-operator")
                && value.activatedAt().equals(NOW)
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("engine-operator")
                && value.traceId().equals("trace-eval-sync")));
    }

    @Test
    void ignoresNonEvaluationPublishedVersions() {
        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.RULE, "RULE.LOCAL.REHEARSAL.BASELINE", "V1"),
            NOW,
            "engine-operator",
            "trace-rule");

        verifyNoInteractions(indicators);
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
            "/__platform__",
            "MEDICAL_RECORD:ENCOUNTER",
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
            "trace-eval-sync"
        );
    }

    private EvaluationIndicator indicator(
            String indicatorId,
            int versionNo,
            EvaluationIndicatorStatus status,
            Instant publishedAt,
            Instant activatedAt) {
        return new EvaluationIndicator(
            1L,
            indicatorId,
            "t-1",
            "EVAL.LOCAL.REHEARSAL.BASELINE",
            versionNo,
            "本地上线演练基础评价指标",
            EvaluationSubjectType.MEDICAL_RECORD,
            "{\"all\":[]}",
            "{\"all\":[]}",
            null,
            "上线演练闭包指标；不用于真实医疗质量判定。",
            "ENCOUNTER",
            "PLATFORM_BASELINE",
            "dept-platform-qc",
            "local-e2e:platform-baseline-runtime-assets",
            status,
            publishedAt,
            publishedAt == null ? null : "engine-operator",
            activatedAt,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW.minusSeconds(3600),
            "engine-operator",
            "trace-old"
        );
    }
}
