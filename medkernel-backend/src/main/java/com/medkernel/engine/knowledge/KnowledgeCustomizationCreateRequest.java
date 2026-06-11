package com.medkernel.engine.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 从平台权威知识创建机构本地定制的请求。
 */
public record KnowledgeCustomizationCreateRequest(
    @NotNull Long platformIdentityId,
    @NotBlank String targetOrgUnitId,
    @NotBlank String applicableScope,
    @NotBlank @Size(min = 4, max = 1000) String reason
) {
}
