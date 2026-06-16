package com.medkernel.engine.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * 发布 prompt/tool/model 版本包请求。
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
