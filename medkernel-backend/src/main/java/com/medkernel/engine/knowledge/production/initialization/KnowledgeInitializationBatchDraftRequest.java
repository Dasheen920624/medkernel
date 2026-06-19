package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 初始化发行批次预览请求。 */
public record KnowledgeInitializationBatchDraftRequest(
    @NotBlank @Size(max = 64) String batchCode,
    @NotNull InitializationReleaseType releaseType,
    @NotBlank @Size(max = 32) String releaseVersion,
    @Size(max = 32) String foundationReleaseVersion,
    @NotNull InitializationPhase phase,
    @Positive int declaredSourceFileCount,
    @Positive int declaredEntryCount,
    Set<FoundationCoverageDimension> coverage,
    @NotBlank @Size(max = 64) String templateVersion,
    @Size(max = 128) String modelVersion,
    @NotBlank @Size(max = 1000) String summary,
    @NotBlank @Size(max = 128) String idempotencyKey,
    @NotEmpty @Size(max = 5000) List<@Valid KnowledgeInitializationEntryRequest> entries
) {
    public KnowledgeInitializationBatchDraftRequest {
        coverage = coverage == null ? Set.of() : Set.copyOf(coverage);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
