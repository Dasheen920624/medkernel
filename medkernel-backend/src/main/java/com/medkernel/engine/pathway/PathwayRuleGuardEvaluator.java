package com.medkernel.engine.pathway;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 路径到规则的单向守卫求值端口。
 *
 * <p>路径只读取独立发布规则的命中结果；规则不得反向调用路径，也不得通过本端口嵌套执行路径。
 */
@FunctionalInterface
public interface PathwayRuleGuardEvaluator {

    PathwayRuleGuardEvaluation evaluate(
        JsonNode reference,
        JsonNode context,
        String runtimeReleaseId);

    static PathwayRuleGuardEvaluator unavailable() {
        return (reference, context, runtimeReleaseId) -> {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径规则守卫执行器未配置");
        };
    }
}
