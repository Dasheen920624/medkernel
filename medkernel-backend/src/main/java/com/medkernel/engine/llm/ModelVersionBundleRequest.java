package com.medkernel.engine.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * 发布提示词、工具和模型版本组合请求。
 */
public record ModelVersionBundleRequest(
    @NotBlank String capabilityCode,
    @NotBlank String promptVersion,
    @NotBlank String promptContent,
    @NotBlank String toolVersion,
    @NotBlank String toolContract,
    @NotBlank String modelVersion,
    @NotBlank String modelDescriptor
) {}
