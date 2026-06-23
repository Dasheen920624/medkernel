package com.medkernel.engine.knowledge.production.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建发布质量记录请求。
 *
 * @param candidateRef 服务端候选引用
 * @param identityId 候选所属知识身份
 * @param versionId 候选物化后的知识版本
 */
public record PublicationQualityRecordRequest(
    @NotBlank String candidateRef,
    @NotNull @Positive Long identityId,
    @NotNull @Positive Long versionId
) {
}
