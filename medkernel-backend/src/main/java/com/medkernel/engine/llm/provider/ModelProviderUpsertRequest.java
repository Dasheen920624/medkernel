package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 模型 provider 接入配置请求（LLM-08）。
 *
 * <p>{@code credentialRef} 仅允许环境变量键名，绝不传明文密钥；
 * {@code expectedVersion} 用于更新既有配置时的乐观锁校验，新建时必须为空。
 */
public record ModelProviderUpsertRequest(
    @NotBlank String providerType,
    @NotBlank String endpointUri,
    String credentialRef,
    @NotBlank String modelVersion,
    @PositiveOrZero Long expectedVersion
) {}
