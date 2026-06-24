package com.medkernel.engine.evaluation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.evaluation.EvaluationSubjectType;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseEvaluationSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T09:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final EvaluationIndicatorRepository indicators =
        mock(EvaluationIndicatorRepository.class);
    private final RuntimeReleaseEvaluationSelector selector =
        new RuntimeReleaseEvaluationSelector(runtime, indicators);

    @Test
    void selectsOnlyActiveEvaluationIndicatorsPinnedByRuntimeRelease() {
        ClinicalRuntimeReleaseItem platformIndicator =
            item(PlatformTenant.ID, VersionedAssetType.EVALUATION, "IND.VTE", "V2", ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem hospitalIndicator =
            item("tenant-A", VersionedAssetType.EVALUATION, "IND.STROKE", "3", ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem disabledIndicator =
            item("tenant-A", VersionedAssetType.EVALUATION, "IND.OLD", "1", ReleaseEntryState.DISABLED);
        ClinicalRuntimeReleaseItem rule =
            item(PlatformTenant.ID, VersionedAssetType.RULE, "RULE.VTE", "V1", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-EVAL1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(platformIndicator, hospitalIndicator, disabledIndicator, rule)));
        when(indicators.findByTenantIdAndIndicatorCodeAndVersionNo(PlatformTenant.ID, "IND.VTE", 2))
            .thenReturn(Optional.of(indicator("ei-vte", PlatformTenant.ID, "IND.VTE", 2, EvaluationIndicatorStatus.ACTIVE)));
        when(indicators.findByTenantIdAndIndicatorCodeAndVersionNo("tenant-A", "IND.STROKE", 3))
            .thenReturn(Optional.of(indicator("ei-stroke", "tenant-A", "IND.STROKE", 3, EvaluationIndicatorStatus.ACTIVE)));

        List<EvaluationIndicator> selected = selector.select("tenant-A", "release-EVAL1");

        assertThat(selected)
            .extracting(
                EvaluationIndicator::tenantId,
                EvaluationIndicator::indicatorCode,
                EvaluationIndicator::versionNo)
            .containsExactly(
                tuple(PlatformTenant.ID, "IND.VTE", 2),
                tuple("tenant-A", "IND.STROKE", 3));
    }

    @Test
    void rejectsRuntimeReleaseWhenPinnedIndicatorVersionIsMissingOrInactive() {
        ClinicalRuntimeReleaseItem missing =
            item("tenant-A", VersionedAssetType.EVALUATION, "IND.MISSING", "V1", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-EVAL1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(missing)));
        when(indicators.findByTenantIdAndIndicatorCodeAndVersionNo("tenant-A", "IND.MISSING", 1))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> selector.select("tenant-A", "release-EVAL1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定评价指标版本不存在");

        ClinicalRuntimeReleaseItem inactive =
            item("tenant-A", VersionedAssetType.EVALUATION, "IND.INACTIVE", "V1", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-EVAL1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(inactive)));
        when(indicators.findByTenantIdAndIndicatorCodeAndVersionNo("tenant-A", "IND.INACTIVE", 1))
            .thenReturn(Optional.of(indicator(
                "ei-inactive", "tenant-A", "IND.INACTIVE", 1, EvaluationIndicatorStatus.PUBLISHED)));

        assertThatThrownBy(() -> selector.select("tenant-A", "release-EVAL1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定评价指标版本未激活");
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            1L,
            "release-EVAL1",
            "tenant-A",
            "hospital-A",
            1L,
            "baseline-EVAL",
            "a".repeat(64),
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-eval"
        );
    }

    private ClinicalRuntimeReleaseItem item(
            String sourceTenantId,
            VersionedAssetType assetType,
            String identity,
            String versionNo,
            ReleaseEntryState state) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "release-EVAL1",
            sourceTenantId,
            ReleaseSourceLayer.PLATFORM,
            assetType,
            identity,
            state,
            "asset-version-" + identity,
            versionNo,
            "b".repeat(64),
            NOW,
            "tester",
            "trace-eval"
        );
    }

    private EvaluationIndicator indicator(
            String indicatorId,
            String tenantId,
            String indicatorCode,
            int versionNo,
            EvaluationIndicatorStatus status) {
        return new EvaluationIndicator(
            1L,
            indicatorId,
            tenantId,
            indicatorCode,
            versionNo,
            "测试指标",
            EvaluationSubjectType.MEDICAL_RECORD,
            "{\"all\":[]}",
            "{\"all\":[]}",
            null,
            "P1",
            "DISCHARGE+24H",
            "全院",
            "dept-1",
            "guideline-1",
            status,
            NOW,
            "tester",
            status == EvaluationIndicatorStatus.ACTIVE ? NOW : null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-eval"
        );
    }
}
