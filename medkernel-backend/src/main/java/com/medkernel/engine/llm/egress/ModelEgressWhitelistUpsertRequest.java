package com.medkernel.engine.llm.egress;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/**
 * 出域白名单维护请求（LLM-03）。
 */
public record ModelEgressWhitelistUpsertRequest(
    @NotEmpty List<String> allowedFields,
    @NotBlank String sensitivityLevel
) {}
