package com.medkernel.engine.safety;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.pathway.PathwayTemplate;
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

    private static final Pattern EXPLICIT_KNOWLEDGE_VERSION_REF =
        Pattern.compile("(?i)(?:knowledge[-_]?version|version)[:#\\-/]([0-9]+)");

    private final KnowledgeAssetVersionRepository versions;

    public ClinicalSafetyGuard(KnowledgeAssetVersionRepository versions) {
        this.versions = versions;
    }

    public void assertRecommendationSourcesAllowed(String tenantId, List<RecommendationSourceRequest> sources) {
        if (tenantId == null || tenantId.isBlank() || sources == null || sources.isEmpty()) {
            return;
        }
        Set<Long> versionIds = new LinkedHashSet<>();
        for (RecommendationSourceRequest source : sources) {
            if (source == null || source.sourceType() != RecommendationSourceType.KNOWLEDGE) {
                continue;
            }
            extractKnowledgeVersionId(source.sourceRefId(), true).ifPresent(versionIds::add);
            extractKnowledgeVersionId(source.citationLocator(), false).ifPresent(versionIds::add);
        }
        for (Long versionId : versionIds) {
            assertVersionActive(tenantId, versionId, "已撤回知识版本禁止参与新推荐");
        }
    }

    public void assertPathwayTemplateAllowed(PathwayTemplate template) {
        if (template == null || template.tenantId() == null || template.tenantId().isBlank()) {
            return;
        }
        extractKnowledgeVersionId(template.sourceRef(), false)
            .ifPresent(versionId -> assertVersionActive(
                template.tenantId(), versionId, "路径模板引用已撤回知识版本"));
    }

    private Optional<Long> extractKnowledgeVersionId(String value, boolean allowPlainNumeric) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        Matcher matcher = EXPLICIT_KNOWLEDGE_VERSION_REF.matcher(trimmed);
        if (matcher.find()) {
            return parseLong(matcher.group(1));
        }
        if (allowPlainNumeric && trimmed.chars().allMatch(Character::isDigit)) {
            return parseLong(trimmed);
        }
        return Optional.empty();
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
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
