package com.medkernel.engine.knowledge.production.gate;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;

/**
 * 门禁：可信级冲突仲裁（AIK-STD-05，FR-3）。
 *
 * <p>当候选指向已有知识身份时，检查同一默认适用域内现行权威版本。低阶来源候选不得覆盖高阶来源现行版本；
 * 新身份或无现行版本时不触发本门禁阻断，后续仍进入审核链。
 */
@Component
public class AuthorityConflictGate implements CandidateGate {

    public static final String CODE = "AUTHORITY_CONFLICT";

    private final KnowledgeAssetVersionRepository versions;

    public AuthorityConflictGate(KnowledgeAssetVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (context.targetIdentityId() == null) {
            return GateItemResult.pass(CODE);
        }
        SourceAuthorityLevel candidateLevel = candidate.trustLevel();
        if (candidateLevel == null) {
            return GateItemResult.fail(CODE, "候选可信分级缺失，无法完成冲突仲裁");
        }
        String organizationScope = normalizeOrganizationScope(candidate.orgScope(), context.tenantId());
        Optional<KnowledgeAssetVersion> active = versions.findActiveByEffectiveScope(
            context.tenantId(),
            context.targetIdentityId(),
            organizationScope,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);
        if (active.isEmpty() || active.get().authorityLevel() == null) {
            return GateItemResult.pass(CODE);
        }
        SourceAuthorityLevel activeLevel = active.get().authorityLevel();
        if (candidateLevel.isLowAuthority() && activeLevel.isHighAuthority()) {
            return GateItemResult.fail(CODE,
                "低阶来源覆盖高阶来源：候选 " + candidateLevel.label()
                    + "，现行 " + activeLevel.label()
                    + "，targetIdentityId=" + context.targetIdentityId()
                    + "，activeVersionId=" + active.get().id()
                    + "，scope=" + organizationScope);
        }
        return GateItemResult.pass(CODE);
    }

    private String normalizeOrganizationScope(String organizationScope, String tenantId) {
        if (organizationScope == null || organizationScope.isBlank()) {
            return "tenant:" + tenantId;
        }
        String trimmed = organizationScope.trim();
        return trimmed.equals(tenantId) ? "tenant:" + tenantId : trimmed;
    }
}
