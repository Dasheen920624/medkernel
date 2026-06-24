package com.medkernel.engine.embed;

import jakarta.validation.constraints.NotBlank;

/**
 * 嵌入 Origin 来源域名新增请求数据契约 (GA-ENG-API-11)。
 */
public record EmbedOriginRequest(
    @NotBlank String origin
) {}
