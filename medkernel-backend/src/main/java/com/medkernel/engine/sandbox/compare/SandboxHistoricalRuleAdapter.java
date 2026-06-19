package com.medkernel.engine.sandbox.compare;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.medkernel.engine.sandbox.replay.SandboxReplayAssetBinding;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleExecutor;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 将历史精确执行结果补齐来源元数据，供稳定业务键对比。 */
@Component
public class SandboxHistoricalRuleAdapter {

    private final SandboxReplayRuleExecutor executor;

    public SandboxHistoricalRuleAdapter(SandboxReplayRuleExecutor executor) {
        this.executor = executor;
    }

    public List<SandboxComparableRuleResult> execute(SandboxReplayResolvedCase replay) {
        Map<String, SandboxReplayAssetBinding> assetsByVersion = new LinkedHashMap<>();
        for (SandboxReplayAssetBinding asset : replay.assets()) {
            if (assetsByVersion.putIfAbsent(asset.versionId(), asset) != null) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "历史清单存在重复资产版本 ID：" + asset.versionId());
            }
        }
        return executor.execute(replay).stream()
            .map(result -> comparable(result, assetsByVersion.get(result.versionId())))
            .toList();
    }

    private static SandboxComparableRuleResult comparable(
            SandboxReplayRuleResult result,
            SandboxReplayAssetBinding asset) {
        if (asset == null) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "历史规则执行结果缺少精确资产来源：" + result.versionId());
        }
        return new SandboxComparableRuleResult(
            result.ruleCode(), result.ruleName(), result.versionId(), result.assetVersion(),
            asset.sourceTier(), asset.sourceOrgRef(), result.contentHash(), result.hit(),
            result.severity(), result.actions(), result.explanation());
    }
}
