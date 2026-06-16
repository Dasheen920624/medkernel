package com.medkernel.engine.knowledge;

import java.util.List;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 知识候选审核工作流响应，携带候选版本与新旧识别依据。
 */
public record KnowledgeCandidateResponse(
    Long identityId,
    PageResponse<KnowledgeAssetVersion> candidates,
    List<CandidateClassification> classifications,
    boolean available,
    String reasonCode,
    String message
) {

    public KnowledgeCandidateResponse {
        candidates = candidates == null ? PageResponse.empty(PageRequest.defaults()) : candidates;
        classifications = classifications == null ? List.of() : List.copyOf(classifications);
    }

    public KnowledgeCandidateResponse(Long identityId, List<KnowledgeAssetVersion> candidates,
            List<CandidateClassification> classifications, boolean available, String reasonCode, String message) {
        this(
            identityId,
            PageResponse.of(candidates == null ? List.of() : candidates, PageRequest.defaults(),
                candidates == null ? 0L : candidates.size()),
            classifications,
            available,
            reasonCode,
            message
        );
    }

    public static KnowledgeCandidateResponse classified(Long identityId, List<KnowledgeAssetVersion> candidates,
            List<CandidateClassification> classifications, CandidateClassificationType reasonCode, String message) {
        PageRequest defaultRequest = PageRequest.defaults();
        return new KnowledgeCandidateResponse(
            identityId,
            PageResponse.of(candidates == null ? List.of() : candidates, defaultRequest,
                candidates == null ? 0L : candidates.size()),
            classifications,
            true,
            reasonCode.name(),
            message
        );
    }
}
