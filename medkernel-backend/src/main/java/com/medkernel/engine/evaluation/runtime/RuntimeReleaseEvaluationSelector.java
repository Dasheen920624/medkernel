package com.medkernel.engine.evaluation.runtime;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从医院当前运行修订中选择本次自动质控评价可执行的指标版本。
 *
 * <p>质控运行只消费运行修订锁定的精确指标版本，不扫描租户下全部 ACTIVE 指标。
 */
@Component
public class RuntimeReleaseEvaluationSelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final EvaluationIndicatorRepository indicators;

    public RuntimeReleaseEvaluationSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            EvaluationIndicatorRepository indicators) {
        this.runtime = runtime;
        this.indicators = indicators;
    }

    public List<EvaluationIndicator> select(String tenantId, String runtimeReleaseId) {
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        List<EvaluationIndicator> selected = new ArrayList<>();
        for (var item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.EVALUATION) {
                continue;
            }
            EvaluationIndicator indicator = indicators
                .findByTenantIdAndIndicatorCodeAndVersionNo(
                    item.sourceTenantId(),
                    requireText(item.assetIdentity(), "评价指标编码"),
                    parseVersionNo(item.versionNo(), item.assetIdentity()))
                .orElseThrow(() -> invalid(
                    "运行修订锁定评价指标版本不存在："
                        + item.assetIdentity() + "@" + item.versionNo()));
            if (indicator.status() != EvaluationIndicatorStatus.ACTIVE) {
                throw invalid(
                    "运行修订锁定评价指标版本未激活："
                        + item.assetIdentity() + "@" + item.versionNo());
            }
            selected.add(indicator);
        }
        return List.copyOf(selected);
    }

    private ClinicalRuntimeReleaseContent resolve(String tenantId, String runtimeReleaseId) {
        try {
            return runtime.resolve(
                requireText(tenantId, "tenantId"),
                requireText(runtimeReleaseId, "runtimeReleaseId"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private static int parseVersionNo(String value, String indicatorCode) {
        try {
            return AssetVersionNumbers.intSequence(value, "评价指标版本");
        } catch (ApiException exception) {
            throw invalid("运行评价指标版本号无效：" + indicatorCode + "@" + value, exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_EVAL_004, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_EVAL_004, message, cause);
    }
}
