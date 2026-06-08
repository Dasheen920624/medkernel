package com.medkernel.engine.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 条件求值期的片段解析入口。
 */
@FunctionalInterface
public interface ConditionFragmentResolver {

    /**
     * 按稳定引用解析片段正文。
     */
    JsonNode resolve(ConditionFragmentReference reference);

    /**
     * 未启用片段库时的诚实失败实现。
     */
    static ConditionFragmentResolver unavailable() {
        return reference -> {
            throw new ApiException(
                ErrorCode.ENG_RULE_001,
                "条件片段库未接入，无法解析片段引用: " + reference.fragmentCode());
        };
    }
}
