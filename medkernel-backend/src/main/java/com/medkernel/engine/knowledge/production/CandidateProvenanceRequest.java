package com.medkernel.engine.knowledge.production;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 候选来源溯源批量查询请求（AIK-STD-12 PR1）：审核台传一组候选引用反查 AI 工厂来源。
 */
public record CandidateProvenanceRequest(
    @NotEmpty List<String> candidateRefs
) {
}
