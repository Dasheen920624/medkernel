package com.medkernel.engine.knowledge.discovery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * AIK-STD-08 最新知识差异检测与过期治理服务。
 *
 * <p>B0 规则只做确定性比对和任务留痕：同指纹返回诚实空态；内容变化落差异台账；
 * 来源明示废止或复审超期触发过期复核任务；不自动替换、不撤回现行权威版本。
 */
@Service
public class KnowledgeDiffDetectionService {

    private static final String EMPTY_UPDATE_BASIS = "content_hash 与现行权威版本一致，无更新";
    private static final String SYSTEM_ACTOR = "system:knowledge-diff";

    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeDiffRepository diffs;
    private final ExpiryTaskRepository expiryTasks;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock;

    @Autowired
    public KnowledgeDiffDetectionService(
            KnowledgeAssetVersionRepository versions,
            KnowledgeDiffRepository diffs,
            ExpiryTaskRepository expiryTasks) {
        this(versions, diffs, expiryTasks, Clock.systemUTC());
    }

    KnowledgeDiffDetectionService(
            KnowledgeAssetVersionRepository versions,
            KnowledgeDiffRepository diffs,
            ExpiryTaskRepository expiryTasks,
            Clock clock) {
        this.versions = versions;
        this.diffs = diffs;
        this.expiryTasks = expiryTasks;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public KnowledgeDiffDetection detect(KnowledgeAssetEnvelope candidate, KnowledgeDiffContext context) {
        String tenantId = requireTenant();
        validate(candidate, context, tenantId);
        Instant now = Instant.now(clock);
        String actor = currentActor();
        String sourceRef = candidate.sources().get(0).sourceRef();

        if (context.targetIdentityId() == null) {
            KnowledgeDiff diff = persistDiff(
                tenantId, context, candidate, null, KnowledgeDiffType.NEW,
                "未绑定现行知识身份，按新增知识候选留痕", sourceRef, now, actor);
            return detection(true, diff.diffType(), context.targetIdentityId(), null, null, null, diff.basis());
        }

        Optional<KnowledgeAssetVersion> active = activeVersion(tenantId, context.targetIdentityId());
        if (active.isPresent() && candidate.contentHash().equals(active.get().contentHash())) {
            ExpiryTask task = isReviewOverdue(active.get(), now)
                ? persistExpiryTask(
                    tenantId, null, context.runCode(), active.get(), ExpiryTaskType.REVIEW_OVERDUE,
                    "现行权威版本已超过 next_review_at，需过期复核", now, actor)
                : null;
            return detection(false, null, context.targetIdentityId(), active.get().id(),
                task == null ? null : task.status(), task == null ? null : task.id(), EMPTY_UPDATE_BASIS);
        }

        if (declaresDeprecation(candidate.payload())) {
            KnowledgeAssetVersion current = requireActive(active, context.targetIdentityId());
            KnowledgeDiff diff = persistDiff(
                tenantId, context, candidate, current, KnowledgeDiffType.DEPRECATED,
                "来源声明废止/退役现行知识，需进入过期复核", sourceRef, now, actor);
            ExpiryTask task = persistExpiryTask(
                tenantId, diff, current, ExpiryTaskType.SOURCE_DEPRECATED,
                "来源声明废止/退役现行知识：" + sourceRef, now, actor);
            return detection(true, diff.diffType(), context.targetIdentityId(), current.id(),
                task.status(), task.id(), diff.basis());
        }

        if (active.isEmpty()) {
            KnowledgeDiff diff = persistDiff(
                tenantId, context, candidate, null, KnowledgeDiffType.NEW,
                "目标身份缺少 ACTIVE 标准版本，按新增差异留痕并等待人工复核", sourceRef, now, actor);
            return detection(true, diff.diffType(), context.targetIdentityId(), null, null, null, diff.basis());
        }

        KnowledgeAssetVersion current = active.get();
        KnowledgeDiff diff = persistDiff(
            tenantId, context, candidate, current, KnowledgeDiffType.REVISED,
            "候选 content_hash 与现行权威版本不同，按修订差异进入人工对照", sourceRef, now, actor);
        ExpiryTask task = isReviewOverdue(current, now)
            ? persistExpiryTask(
                tenantId, diff, current, ExpiryTaskType.REVIEW_OVERDUE,
                "现行权威版本已超过 next_review_at，需过期复核", now, actor)
            : null;
        return detection(true, diff.diffType(), context.targetIdentityId(), current.id(),
            task == null ? null : task.status(), task == null ? null : task.id(), diff.basis());
    }

    private Optional<KnowledgeAssetVersion> activeVersion(String tenantId, Long identityId) {
        List<KnowledgeAssetVersion> existing =
            versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId);
        return existing.stream()
            .filter(KnowledgeAssetVersion::isAuthoritative)
            .findFirst();
    }

    private KnowledgeAssetVersion requireActive(Optional<KnowledgeAssetVersion> active, Long identityId) {
        return active.orElseThrow(() -> new ApiException(
            ErrorCode.CONFLICT,
            "知识身份 id=" + identityId + " 缺少 ACTIVE 标准版本，不能登记废止过期任务"));
    }

    private KnowledgeDiff persistDiff(
            String tenantId,
            KnowledgeDiffContext context,
            KnowledgeAssetEnvelope candidate,
            KnowledgeAssetVersion current,
            KnowledgeDiffType type,
            String basis,
            String sourceRef,
            Instant now,
            String actor) {
        return diffs.save(new KnowledgeDiff(
            null,
            tenantId,
            context.runCode(),
            context.targetIdentityId(),
            current == null ? null : current.id(),
            candidate.assetIdentity(),
            current == null ? null : current.contentHash(),
            candidate.contentHash(),
            type,
            basis,
            sourceRef,
            now,
            actor,
            RequestContext.currentTraceId()));
    }

    private ExpiryTask persistExpiryTask(
            String tenantId,
            KnowledgeDiff diff,
            KnowledgeAssetVersion current,
            ExpiryTaskType type,
            String reason,
            Instant now,
            String actor) {
        return persistExpiryTask(tenantId, diff.id(), diff.runCode(), current, type, reason, now, actor);
    }

    private ExpiryTask persistExpiryTask(
            String tenantId,
            Long diffId,
            String runCode,
            KnowledgeAssetVersion current,
            ExpiryTaskType type,
            String reason,
            Instant now,
            String actor) {
        String taskKey = tenantId + ":" + current.identityId() + ":" + current.id() + ":" + type + ":" + runCode;
        return expiryTasks.save(new ExpiryTask(
            null,
            tenantId,
            taskKey,
            diffId,
            current.identityId(),
            current.id(),
            type,
            ExpiryTaskStatus.OPEN,
            current.riskLevel(),
            reason,
            now,
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()));
    }

    private boolean isReviewOverdue(KnowledgeAssetVersion version, Instant now) {
        return version.nextReviewAt() != null && !version.nextReviewAt().isAfter(now);
    }

    private boolean declaresDeprecation(String payload) {
        JsonNode triage = triageNode(payload);
        return booleanValue(triage, "deprecated")
            || booleanValue(triage, "deprecation")
            || booleanValue(triage, "retirement")
            || stateEquals(triage, "DEPRECATION");
    }

    private JsonNode triageNode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = json.readTree(payload);
            JsonNode triage = parsed.get("triage");
            return triage == null || triage.isMissingNode() ? parsed : triage;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean booleanValue(JsonNode node, String field) {
        return node != null && node.has(field) && node.get(field).asBoolean(false);
    }

    private boolean stateEquals(JsonNode node, String state) {
        return node != null
            && node.has("state")
            && state.equals(node.get("state").asText("").trim().toUpperCase(Locale.ROOT));
    }

    private void validate(KnowledgeAssetEnvelope candidate, KnowledgeDiffContext context, String tenantId) {
        if (candidate == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "差异检测候选不能为空");
        }
        if (context == null || context.runCode() == null || context.runCode().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "差异检测必须绑定探索运行编码");
        }
        if (candidate.orgScope() == null || !tenantId.equals(candidate.orgScope())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选组织作用域与当前租户不一致，禁跨租户差异检测");
        }
        if (candidate.sources().isEmpty() || candidate.sources().get(0).sourceRef() == null
                || candidate.sources().get(0).sourceRef().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "差异检测必须带真实来源锚点");
        }
        if (candidate.contentHash() == null || candidate.contentHash().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "差异检测必须带内容指纹");
        }
    }

    private KnowledgeDiffDetection detection(
            boolean updated,
            KnowledgeDiffType diffType,
            Long targetIdentityId,
            Long currentVersionId,
            ExpiryTaskStatus expiryTaskStatus,
            Long expiryTaskId,
            String basis) {
        return new KnowledgeDiffDetection(
            updated, diffType, targetIdentityId, currentVersionId, expiryTaskStatus, expiryTaskId, basis);
    }

    private String requireTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(value -> !value.isBlank())
            .orElse(SYSTEM_ACTOR);
    }
}
