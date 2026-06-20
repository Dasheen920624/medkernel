package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 模型 Provider 凭据移除请求。
 *
 * <p>移除凭据会同步清除环境变量回退引用，并强制停用 Provider、重置为未连接状态。
 */
public record ModelProviderCredentialRemovalRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotNull @PositiveOrZero Long expectedVersion,
    boolean confirmedHighRisk
) {}
