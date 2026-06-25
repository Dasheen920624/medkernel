package com.medkernel.engine.llm;

import java.util.List;

/**
 * 模型真实任务触发高敏外调时返回给前台的责任确认挑战。
 *
 * @param capabilityCode 模型能力代码
 * @param payloadHash 脱敏且最小化后的外调载荷摘要
 * @param egressFields 实际拟出域字段
 * @param providerCode 目标模型服务
 * @param message 面向操作者的阻断原因
 */
public record ModelEgressConfirmationChallenge(
    String capabilityCode,
    String payloadHash,
    List<String> egressFields,
    String providerCode,
    String message
) {
    public ModelEgressConfirmationChallenge {
        egressFields = egressFields == null ? List.of() : List.copyOf(egressFields);
    }
}
