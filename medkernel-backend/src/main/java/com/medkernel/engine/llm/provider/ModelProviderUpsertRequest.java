package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;

/**
 * 模型 provider 接入配置请求（LLM-08）。{@code credentialRef} 仅为密钥引用名，绝不传明文密钥。
 */
public record ModelProviderUpsertRequest(
    @NotBlank String providerType,
    @NotBlank String endpointUri,
    String credentialRef,
    @NotBlank String modelVersion,
    Boolean enabled
) {}
