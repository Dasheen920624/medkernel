package com.medkernel.engine.knowledge.acquisition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.parsing.DocumentFormat;

/**
 * 公域资料获取请求。URL 必须命中已审批来源白名单，内容进入既有文档解析链路。
 */
public record KnowledgeAcquisitionRunRequest(
    @NotBlank String sourceCode,
    @NotBlank String url,
    @NotBlank String versionNo,
    @NotNull DocumentFormat format
) {
}
