package com.medkernel.engine.knowledge;

import java.util.List;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 知识来源追溯聚合视图。
 *
 * <p>只组合关系库中的知识身份、分页版本、分页替代历史和逐条来源锚点；引用链缺失时返回部分成功，
 * 不在服务端补造来源或版本。
 */
public record KnowledgeProvenanceResponse(
    KnowledgeIdentity identity,
    Long currentVersionId,
    PageResponse<KnowledgeAssetVersion> versions,
    PageResponse<KnowledgeSupersession> supersessions,
    List<KnowledgeSourceEvidence> sourceEvidence,
    int unresolvedCitationCount,
    boolean partial
) {

    public KnowledgeProvenanceResponse {
        versions = versions == null ? PageResponse.empty(PageRequest.defaults()) : versions;
        supersessions = supersessions == null ? PageResponse.empty(PageRequest.defaults()) : supersessions;
        sourceEvidence = sourceEvidence == null ? List.of() : List.copyOf(sourceEvidence);
    }
}
