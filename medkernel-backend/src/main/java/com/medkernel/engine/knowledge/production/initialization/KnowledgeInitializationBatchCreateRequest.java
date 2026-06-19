package com.medkernel.engine.knowledge.production.initialization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 初始化批次创建请求；三层摘要必须来自同请求的服务端预览。 */
public record KnowledgeInitializationBatchCreateRequest(
    @NotNull @Valid KnowledgeInitializationBatchDraftRequest draft,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedSourceManifestHash,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedCandidateManifestHash,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedOverallHash
) {
}
