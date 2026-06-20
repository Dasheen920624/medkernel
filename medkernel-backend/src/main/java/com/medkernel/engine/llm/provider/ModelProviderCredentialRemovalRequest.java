package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 模型 Provider 凭据移除请求。
 *
 * <p>移除凭据会强制停用 Provider，并把健康状态重置为未连接。
 */
public record ModelProviderCredentialRemovalRequest(
    @NotBlank @Size(min = 8, max = 500) String reason,
    @NotNull @PositiveOrZero Long expectedVersion,
    boolean confirmedHighRisk
) {}
