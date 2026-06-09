package com.medkernel.engine.knowledge;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 知识身份弃用与后继迁移请求。
 */
public record KnowledgeRetirementRequest(
    @NotNull Long successorIdentityId,
    @NotNull @Future Instant gracePeriodEnd,
    @NotBlank @Size(max = 1000) String migrationGuidance
) {
}
