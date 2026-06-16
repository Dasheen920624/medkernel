package com.medkernel.engine.knowledge.production.gate;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 门禁：审核要素（AIK-STD-05，FR-1 审核要素）。
 *
 * <p>候选须为候选态（{@code DRAFT}/{@code IN_REVIEW}，铁律 #5 只产候选）且主题与版本标签非空，确保可进审核台。
 */
@Component
public class ReviewElementsGate implements CandidateGate {

    public static final String CODE = "REVIEW_ELEMENTS";
    private static final Set<AssetVersionStatus> CANDIDATE_STATUSES =
        Set.of(AssetVersionStatus.DRAFT, AssetVersionStatus.IN_REVIEW);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.lifecycleStatus() == null || !CANDIDATE_STATUSES.contains(candidate.lifecycleStatus())) {
            return GateItemResult.fail(CODE, "生命周期状态须候选态（DRAFT/IN_REVIEW）");
        }
        if (candidate.subject() == null || candidate.subject().isBlank()) {
            return GateItemResult.fail(CODE, "资产主题缺失");
        }
        if (candidate.versionLabel() == null || candidate.versionLabel().isBlank()) {
            return GateItemResult.fail(CODE, "版本标签缺失");
        }
        return GateItemResult.pass(CODE);
    }
}
