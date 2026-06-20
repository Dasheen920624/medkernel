package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 模型 provider 接入配置请求（LLM-08）。
 *
 * <p>{@code credentialRef} 是可选的环境变量回退键名；前台密钥通过独立凭据接口加密维护，绝不写入本请求；
 * {@code expectedVersion} 用于更新既有配置时的乐观锁校验，新建时必须为空。
 */
public record ModelProviderUpsertRequest(
    @NotBlank String providerType,
    @NotBlank String endpointUri,
    String credentialRef,
    @NotBlank String modelVersion,
    @PositiveOrZero Long expectedVersion
) {}
