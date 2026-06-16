package com.medkernel.engine.knowledge.discovery;

/**
 * 知识差异检测结果。
 *
 * @param updated 是否发现新增、修订或废止差异
 * @param diffType 差异类型；无更新时为空
 * @param targetIdentityId 目标知识身份
 * @param currentVersionId 当前权威版本
 * @param expiryTaskStatus 过期治理任务状态；未触发时为空
 * @param expiryTaskId 过期治理任务主键；未触发时为空
 * @param basis 检测依据
 */
public record KnowledgeDiffDetection(
    boolean updated,
    KnowledgeDiffType diffType,
    Long targetIdentityId,
    Long currentVersionId,
    ExpiryTaskStatus expiryTaskStatus,
    Long expiryTaskId,
    String basis
) {
}
