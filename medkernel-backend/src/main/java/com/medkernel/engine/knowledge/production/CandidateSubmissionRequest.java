package com.medkernel.engine.knowledge.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 提交候选请求（AIK-STD-13 PR4）：候选信封 + 物化目标身份声明。
 *
 * @param candidate 候选信封（经 AIK-STD-01 校验闸 + §9 隔离守卫）
 * @param target 物化目标知识身份（现有 异或 新建身份壳，生产方显式声明）
 */
public record CandidateSubmissionRequest(
    @NotNull @Valid KnowledgeAssetEnvelope candidate,
    @NotNull @Valid MaterializationTarget target
) {
}
