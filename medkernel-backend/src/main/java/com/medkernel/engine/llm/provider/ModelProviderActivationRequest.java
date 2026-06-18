package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 模型 provider 高危启停请求。
 *
 * @param reason 可审计的启停原因
 * @param expectedVersion 当前关系库乐观锁版本
 * @param confirmedHighRisk 已明确确认高危影响
 */
public record ModelProviderActivationRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull Boolean confirmedHighRisk
) {}
