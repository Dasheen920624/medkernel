package com.medkernel.engine.knowledge.production.triage;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AIK-STD-10 生成期身份识别、去重与 8 态分流服务。
 *
 * <p>本服务位于候选提审前：重复候选只留分流记录，不重复物化入审核链；其余状态只决定审核去向与证据说明，
 * 不自动发布、不绕过 AIK-STD-05 门禁和人工审核。
 */
@Service
public class KnowledgeGenerationTriageService {

    private final KnowledgeAssetVersionRepository versions;
    private final GenerationTriageRepository triages;
    private final ObjectMapper json = new ObjectMapper();

    public KnowledgeGenerationTriageService(
            KnowledgeAssetVersionRepository versions,
            GenerationTriageRepository triages) {
        this.versions = versions;
        this.triages = triages;
    }

    @Transactional
    public GenerationTriageDecision evaluate(KnowledgeAssetEnvelope candidate, GenerationTriageContext context) {
        TriageDraft draft = classify(candidate, context);
        Instant now = Instant.now();
        GenerationTriage saved = triages.save(new GenerationTriage(
            null,
            context.tenantId(),
            context.jobCode(),
            candidate.contentHash(),
            context.assetType(),
            context.targetIdentityId(),
            draft.activeVersionId(),
            draft.matchedVersionId(),
            draft.state(),
            draft.action(),
            draft.basis(),
            now,
            RequestContext.currentUserId().orElse(null)));
        return new GenerationTriageDecision(
            saved.id(),
            saved.triageState(),
            saved.action(),
            saved.activeVersionId(),
            saved.matchedVersionId(),
            saved.basis());
    }

    @Transactional(readOnly = true)
    public List<GenerationTriage> listResults(String jobCode) {
        return triages.findByTenantIdAndJobCodeOrderByIdAsc(requireCurrentTenant(), jobCode);
    }

    private TriageDraft classify(KnowledgeAssetEnvelope candidate, GenerationTriageContext context) {
        if (context.targetIdentityId() == null) {
            return draft(GenerationTriageState.NEW_ASSET, GenerationTriageAction.SUBMIT_REVIEW,
                null, null, "未指定现有知识身份，按新知识身份候选进入审核");
        }

        List<KnowledgeAssetVersion> existing =
            versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(context.tenantId(), context.targetIdentityId());
        Optional<KnowledgeAssetVersion> duplicate = existing.stream()
            .filter(version -> candidate.contentHash().equals(version.contentHash()))
            .findFirst();
        Optional<KnowledgeAssetVersion> active = existing.stream()
            .filter(KnowledgeAssetVersion::isAuthoritative)
            .findFirst();

        if (duplicate.isPresent()) {
            KnowledgeAssetVersion matched = duplicate.get();
            return draft(GenerationTriageState.DUPLICATE, GenerationTriageAction.SKIP_DUPLICATE,
                active.map(KnowledgeAssetVersion::id).orElse(matched.id()), matched.id(),
                "content_hash 与既有版本 " + matched.versionNo() + " 一致，跳过重复入审");
        }

        JsonNode payload = parsePayload(candidate.payload());
        if (declaresDeprecation(payload)) {
            return draft(GenerationTriageState.DEPRECATION, GenerationTriageAction.RETIREMENT_REVIEW,
                active.map(KnowledgeAssetVersion::id).orElse(null), null,
                "候选声明废止/退役现行知识，进入废止审核");
        }

        if (active.isEmpty()) {
            return draft(GenerationTriageState.UNCERTAIN, GenerationTriageAction.MANUAL_REVIEW,
                null, null, "现有身份缺少 ACTIVE 基线，无法自动判断新旧关系");
        }

        KnowledgeAssetVersion activeVersion = active.get();
        if (declaresConflict(payload)) {
            return draft(GenerationTriageState.CONFLICT, GenerationTriageAction.CONFLICT_REVIEW,
                activeVersion.id(), null, "候选声明与现行版本存在冲突，进入冲突仲裁审核");
        }

        SourceAuthorityLevel candidateLevel = candidate.trustLevel();
        SourceAuthorityLevel activeLevel = activeVersion.authorityLevel();
        if (candidateLevel == null || activeLevel == null) {
            return draft(GenerationTriageState.UNCERTAIN, GenerationTriageAction.MANUAL_REVIEW,
                activeVersion.id(), null, "候选或现行版本缺少来源可信级，需人工分流");
        }
        if (candidateLevel.isHighAuthority() && activeLevel.isLowAuthority()) {
            return draft(GenerationTriageState.MAJOR_UPGRADE, GenerationTriageAction.UPGRADE_REVIEW,
                activeVersion.id(), null,
                "候选来源高于现行版本：候选 " + candidateLevel.label() + "，现行 " + activeLevel.label());
        }
        if (candidateLevel.isLowAuthority() && activeLevel.isHighAuthority()) {
            return draft(GenerationTriageState.DOWNGRADE, GenerationTriageAction.DOWNGRADE_REVIEW,
                activeVersion.id(), null,
                "候选来源低于现行版本：候选 " + candidateLevel.label() + "，现行 " + activeLevel.label());
        }
        return draft(GenerationTriageState.MINOR_REVISION, GenerationTriageAction.MERGE_REVIEW,
            activeVersion.id(), null, "同一知识身份内容变更，按小修订进入对照审核");
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return json.readTree(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean declaresConflict(JsonNode payload) {
        JsonNode triage = triageNode(payload);
        return booleanValue(triage, "conflict") || stateEquals(triage, GenerationTriageState.CONFLICT);
    }

    private boolean declaresDeprecation(JsonNode payload) {
        JsonNode triage = triageNode(payload);
        return booleanValue(triage, "deprecated")
            || booleanValue(triage, "deprecation")
            || booleanValue(triage, "retirement")
            || stateEquals(triage, GenerationTriageState.DEPRECATION);
    }

    private JsonNode triageNode(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        JsonNode triage = payload.get("triage");
        return triage == null || triage.isMissingNode() ? payload : triage;
    }

    private boolean booleanValue(JsonNode node, String field) {
        return node != null && node.has(field) && node.get(field).asBoolean(false);
    }

    private boolean stateEquals(JsonNode node, GenerationTriageState state) {
        if (node == null || !node.has("state")) {
            return false;
        }
        return state.name().equals(node.get("state").asText("").trim().toUpperCase(Locale.ROOT));
    }

    private TriageDraft draft(
            GenerationTriageState state,
            GenerationTriageAction action,
            Long activeVersionId,
            Long matchedVersionId,
            String basis) {
        return new TriageDraft(state, action, activeVersionId, matchedVersionId, basis);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private record TriageDraft(
        GenerationTriageState state,
        GenerationTriageAction action,
        Long activeVersionId,
        Long matchedVersionId,
        String basis
    ) {
    }
}
