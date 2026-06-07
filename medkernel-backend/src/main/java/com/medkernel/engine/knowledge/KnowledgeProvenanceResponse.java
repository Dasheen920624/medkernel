package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 知识来源追溯聚合视图。
 *
 * <p>只组合关系库中的知识身份、版本、替代历史和逐条来源锚点；引用链缺失时返回部分成功，
 * 不在服务端补造来源或版本。
 */
public record KnowledgeProvenanceResponse(
    KnowledgeIdentity identity,
    Long currentVersionId,
    List<KnowledgeAssetVersion> versions,
    List<KnowledgeSupersession> supersessions,
    List<KnowledgeSourceEvidence> sourceEvidence,
    int unresolvedCitationCount,
    boolean partial
) {

    public KnowledgeProvenanceResponse {
        versions = versions == null ? List.of() : List.copyOf(versions);
        supersessions = supersessions == null ? List.of() : List.copyOf(supersessions);
        sourceEvidence = sourceEvidence == null ? List.of() : List.copyOf(sourceEvidence);
    }
}
