package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 知识候选审核工作流响应，携带候选版本与新旧识别依据。
 */
public record KnowledgeCandidateResponse(
    Long identityId,
    List<KnowledgeAssetVersion> candidates,
    List<CandidateClassification> classifications,
    boolean available,
    String reasonCode,
    String message
) {

    public KnowledgeCandidateResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        classifications = classifications == null ? List.of() : List.copyOf(classifications);
    }

    public static KnowledgeCandidateResponse classified(Long identityId, List<KnowledgeAssetVersion> candidates,
            List<CandidateClassification> classifications, CandidateClassificationType reasonCode, String message) {
        return new KnowledgeCandidateResponse(
            identityId,
            candidates,
            classifications,
            true,
            reasonCode.name(),
            message
        );
    }
}
