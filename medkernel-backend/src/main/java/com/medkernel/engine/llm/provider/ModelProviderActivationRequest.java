package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 模型服务高危启停请求。
 *
 * @param capabilityCode 启用时必须精确匹配已通过医学评测的模型能力码；停用时可为空
 * @param reason 可审计的启停原因
 * @param expectedVersion 当前关系库乐观锁版本
 * @param confirmedHighRisk 已明确确认高危影响
 */
public record ModelProviderActivationRequest(
    @Size(max = 64) String capabilityCode,
    @NotBlank @Size(min = 8, max = 500) String reason,
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull Boolean confirmedHighRisk
) {}
