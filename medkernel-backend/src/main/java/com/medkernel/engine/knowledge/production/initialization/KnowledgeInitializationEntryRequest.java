package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 初始化发行清单条目请求；候选风险、来源和生产器由服务端反查。 */
public record KnowledgeInitializationEntryRequest(
    @NotBlank @Size(max = 16) String catalogCode,
    @NotBlank @Size(max = 256) String canonicalId,
    @NotBlank @Size(max = 256) String namespace,
    @NotBlank @Size(max = 32) String assetVersion,
    @NotBlank @Size(max = 128) String candidateRef,
    @Size(max = 1000) List<@NotBlank @Size(max = 256) String> dependencyCanonicalIds,
    @Size(max = 256) String parentCanonicalId,
    @Size(max = 128) String unitDimension,
    @Size(max = 256) String conversionTargetCanonicalId,
    @NotBlank @Size(max = 500) String sourcePolicy,
    @NotBlank @Size(max = 500) String reviewPolicy,
    @NotBlank @Size(max = 500) String testEvidenceRef,
    @NotBlank @Size(max = 128) String ownerRole,
    @NotBlank @Size(max = 1000) String runtimeConsumers,
    @NotBlank @Size(max = 1000) String rollbackStrategy,
    @NotNull InitializationChangeType changeType,
    @Size(max = 256) String replacementCanonicalId,
    Instant effectiveTo
) {
    public KnowledgeInitializationEntryRequest {
        dependencyCanonicalIds = dependencyCanonicalIds == null ? List.of() : List.copyOf(dependencyCanonicalIds);
    }
}
