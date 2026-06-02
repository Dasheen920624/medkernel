package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 知识候选列表响应。
 *
 * <p>KNOW-02 未实施候选存储前，API-03 必须返回诚实空态，而不是伪造待审候选。
 */
public record KnowledgeCandidateResponse(
    Long identityId,
    List<KnowledgeAssetVersion> candidates,
    boolean available,
    String reasonCode,
    String message
) {

    public KnowledgeCandidateResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static KnowledgeCandidateResponse know02Pending(Long identityId) {
        return new KnowledgeCandidateResponse(
            identityId,
            List.of(),
            false,
            "KNOW_02_PENDING",
            "知识候选审核引擎尚未实施，当前无可审核候选"
        );
    }
}
