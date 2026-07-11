package com.medkernel.engine.safety;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.recommendation.KnowledgeSourceLocator;
import com.medkernel.engine.recommendation.KnowledgeSourceLocator.KnowledgeVersionRef;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 临床运行安全守卫。
 *
 * <p>新推荐和新入径只能引用当前 ACTIVE 知识版本；已撤回 / 被替代旧版仅允许历史重放链路读取。
 */
@Component
public class ClinicalSafetyGuard {

    private final KnowledgeAssetVersionRepository versions;

    public ClinicalSafetyGuard(KnowledgeAssetVersionRepository versions) {
        this.versions = versions;
    }

    public void assertRecommendationSourcesAllowed(String tenantId, List<RecommendationSourceRequest> sources) {
        if (tenantId == null || tenantId.isBlank() || sources == null || sources.isEmpty()) {
            return;
        }
        Set<KnowledgeVersionRef> versionRefs = new LinkedHashSet<>();
        for (RecommendationSourceRequest source : sources) {
            if (source == null || source.sourceType() != RecommendationSourceType.KNOWLEDGE) {
                continue;
            }
            KnowledgeSourceLocator.parse(source.sourceRefId(), tenantId, true).ifPresent(versionRefs::add);
            KnowledgeSourceLocator.parse(source.citationLocator(), tenantId, false).ifPresent(versionRefs::add);
        }
        for (KnowledgeVersionRef ref : versionRefs) {
            assertVersionActive(ref.tenantId(), ref.versionId(), "已撤回知识版本禁止参与新推荐");
        }
    }

    public void assertPathwayTemplateAllowed(PathwayTemplate template) {
        if (template == null || template.tenantId() == null || template.tenantId().isBlank()) {
            return;
        }
        KnowledgeSourceLocator.parse(template.sourceRef(), template.tenantId(), false)
            .ifPresent(ref -> assertVersionActive(
                ref.tenantId(), ref.versionId(), "临床路径引用已撤回知识版本"));
    }

    private void assertVersionActive(String tenantId, Long versionId, String message) {
        KnowledgeAssetVersion version = versions.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                "知识版本不存在或不可用于新临床命中 versionId=" + versionId));
        if (version.status() != KnowledgeVersionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, message + " versionId=" + versionId
                + " status=" + version.status());
        }
    }
}
