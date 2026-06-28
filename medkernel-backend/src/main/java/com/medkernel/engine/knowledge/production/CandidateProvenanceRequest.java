package com.medkernel.engine.knowledge.production;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 候选来源溯源批量查询请求（AIK-STD-12）：审核台传一组候选引用反查 AI 工厂来源。
 */
public record CandidateProvenanceRequest(
    @NotEmpty
    @Size(max = CandidateProvenanceRequest.MAX_CANDIDATE_REFS)
    List<@NotBlank @Size(max = 128) String> candidateRefs
) {
    public static final int MAX_CANDIDATE_REFS = 200;
}
