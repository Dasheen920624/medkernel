package com.medkernel.engine.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.AffectedCaseTargetType;
import com.medkernel.engine.knowledge.AffectedCaseTask;
import com.medkernel.engine.knowledge.AffectedCaseTaskRepository;
import com.medkernel.engine.knowledge.AffectedCaseTaskStatus;
import com.medkernel.engine.knowledge.AffectedCaseTaskType;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeInvalidation;
import com.medkernel.engine.knowledge.KnowledgeInvalidationRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardRepository;
import com.medkernel.engine.recommendation.RecommendationSource;
import com.medkernel.engine.recommendation.RecommendationSourceRepository;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTrigger;
import com.medkernel.engine.recommendation.RecommendationTriggerRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MED-C3 安全撤回编排服务。
 *
 * <p>复用 SYS-08 知识版本撤回框架，在其通用影响任务之外补充 D3 临床运行真实索引：
 * 推荐来源命中的患者病例、路径模板下仍在运行的患者路径，以及既有同步目标任务。
 */
@Service
public class SafetyWithdrawalService {

    private static final String SAFETY_WITHDRAWAL_ENTITY = "safety_withdrawal";
    private static final Set<PatientPathwayStatus> ACTIVE_PATHWAY_STATUSES = Set.of(
        PatientPathwayStatus.ENTERED,
        PatientPathwayStatus.NODE_EXECUTING,
        PatientPathwayStatus.VARIANCE);

    private final KnowledgeVersionService knowledgeVersions;
    private final KnowledgeInvalidationRepository invalidations;
    private final AffectedCaseTaskRepository affectedTasks;
    private final RecommendationSourceRepository recommendationSources;
    private final RecommendationCardRepository recommendationCards;
    private final RecommendationTriggerRepository recommendationTriggers;
    private final PathwayTemplateRepository pathwayTemplates;
    private final PatientPathwayRepository patientPathways;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper json;

    public SafetyWithdrawalService(KnowledgeVersionService knowledgeVersions,
                                   KnowledgeInvalidationRepository invalidations,
                                   AffectedCaseTaskRepository affectedTasks,
                                   RecommendationSourceRepository recommendationSources,
                                   RecommendationCardRepository recommendationCards,
                                   RecommendationTriggerRepository recommendationTriggers,
                                   PathwayTemplateRepository pathwayTemplates,
                                   PatientPathwayRepository patientPathways,
                                   AuditRecorder auditRecorder,
                                   ObjectMapper json) {
        this.knowledgeVersions = knowledgeVersions;
        this.invalidations = invalidations;
        this.affectedTasks = affectedTasks;
        this.recommendationSources = recommendationSources;
        this.recommendationCards = recommendationCards;
        this.recommendationTriggers = recommendationTriggers;
        this.pathwayTemplates = pathwayTemplates;
        this.patientPathways = patientPathways;
        this.auditRecorder = auditRecorder;
        this.json = json;
    }

    @Transactional
    public SafetyWithdrawalResponse withdraw(SafetyWithdrawalRequest request) {
        String tenantId = requireCurrentTenant();
        KnowledgeAssetVersion withdrawn = withdrawOrReuseExistingInvalidation(request, tenantId);
        KnowledgeInvalidation invalidation = latestInvalidation(tenantId, request.versionId())
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                "安全撤回未生成失效记录 versionId=" + request.versionId()));

        createPatientCaseTasks(tenantId, invalidation, withdrawn, request.reason());
        createPatientPathwayTasks(tenantId, invalidation, withdrawn, request.reason());

        SafetyImpactResponse impact = impactFor(tenantId, invalidation);
        auditRecorder.record(AuditAction.PUBLISH, SAFETY_WITHDRAWAL_ENTITY, String.valueOf(invalidation.id()),
            "安全撤回已隔离 versionId=" + request.versionId() + " affectedTasks=" + impact.taskCount());
        return new SafetyWithdrawalResponse(
            invalidation.id(), request.identityId(), request.versionId(),
            KnowledgeVersionStatus.WITHDRAWN.name(), impact, RequestContext.currentTraceId());
    }

    @Transactional(readOnly = true)
    public SafetyImpactResponse impact(Long withdrawalId) {
        String tenantId = requireCurrentTenant();
        KnowledgeInvalidation invalidation = invalidations.findById(withdrawalId)
            .filter(row -> tenantId.equals(row.tenantId()))
            .orElseThrow(() -> ApiException.notFound("安全撤回 id=" + withdrawalId));
        return impactFor(tenantId, invalidation);
    }

    @Transactional(readOnly = true)
    public String exportImpactEvidence(Long withdrawalId) {
        SafetyImpactResponse impact = impact(withdrawalId);
        Map<String, Object> summaryLine = new LinkedHashMap<>();
        summaryLine.put("recordType", "summary");
        summaryLine.put("withdrawalId", impact.withdrawalId());
        summaryLine.put("identityId", impact.identityId());
        summaryLine.put("versionId", impact.versionId());
        summaryLine.put("patientCaseCount", impact.patientCaseCount());
        summaryLine.put("patientPathwayCount", impact.patientPathwayCount());
        summaryLine.put("syncTargetCount", impact.syncTargetCount());
        summaryLine.put("taskCount", impact.taskCount());
        summaryLine.put("impactDigest", impact.impactDigest());
        summaryLine.put("traceId", impact.traceId());
        String summary = writeNdjson(summaryLine);
        String taskLines = impact.tasks().stream()
            .map(this::taskEvidenceLine)
            .collect(Collectors.joining("\n"));
        auditRecorder.record(AuditAction.EXPORT, SAFETY_WITHDRAWAL_ENTITY, String.valueOf(withdrawalId),
            "导出安全撤回影响证据 taskCount=" + impact.taskCount());
        return taskLines.isBlank() ? summary + "\n" : summary + "\n" + taskLines + "\n";
    }

    private SafetyImpactResponse impactFor(String tenantId, KnowledgeInvalidation invalidation) {
        List<AffectedCaseTask> tasks = safeList(
            affectedTasks.findByTenantIdAndInvalidationIdOrderByCreatedAtAsc(tenantId, invalidation.id()));
        List<AffectedCaseTask> sortedTasks = tasks.stream()
            .sorted(Comparator
                .comparing(AffectedCaseTask::taskKey, Comparator.nullsLast(String::compareTo))
                .thenComparing(AffectedCaseTask::targetRef, Comparator.nullsLast(String::compareTo)))
            .toList();
        List<SafetyAffectedTaskResponse> taskResponses = sortedTasks.stream()
            .map(task -> new SafetyAffectedTaskResponse(
                task.taskKey(),
                enumName(task.taskType()),
                enumName(task.targetType()),
                task.targetRef(),
                enumName(task.status()),
                task.dueAt()))
            .toList();
        return new SafetyImpactResponse(
            invalidation.id(),
            invalidation.identityId(),
            invalidation.versionId(),
            countByTarget(sortedTasks, AffectedCaseTargetType.PATIENT_CASE),
            countByTarget(sortedTasks, AffectedCaseTargetType.PATIENT_PATHWAY),
            countByTarget(sortedTasks, AffectedCaseTargetType.SYNC_TARGET),
            sortedTasks.size(),
            impactDigest(sortedTasks),
            taskResponses,
            RequestContext.currentTraceId());
    }

    private KnowledgeAssetVersion withdrawOrReuseExistingInvalidation(SafetyWithdrawalRequest request, String tenantId) {
        try {
            return knowledgeVersions.withdraw(request.identityId(), request.versionId(), request.reason());
        } catch (ApiException e) {
            Optional<KnowledgeInvalidation> existingInvalidation = latestInvalidation(tenantId, request.versionId())
                .filter(row -> request.identityId().equals(row.identityId()));
            if (e.errorCode() == ErrorCode.CONFLICT && existingInvalidation.isPresent()) {
                Instant now = Instant.now();
                return new KnowledgeAssetVersion(
                    request.versionId(), tenantId, request.identityId(), null, null, null, null, null,
                    null, KnowledgeVersionStatus.WITHDRAWN, null, null, null, null, null,
                    null, null, "version:" + request.versionId(), null, now,
                    null, null, null, null, now, request.reason(), now, currentActor(), now, currentActor());
            }
            throw e;
        }
    }

    private Optional<KnowledgeInvalidation> latestInvalidation(String tenantId, Long versionId) {
        return safeList(invalidations.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc(tenantId, versionId))
            .stream()
            .findFirst();
    }

    private void createPatientCaseTasks(String tenantId, KnowledgeInvalidation invalidation,
                                        KnowledgeAssetVersion version, String reason) {
        String sourceRef = canonicalKnowledgeVersionRef(version.id());
        for (RecommendationSource source : safeList(
                recommendationSources.findByTenantIdAndSourceTypeAndSourceRefIdOrderByCreatedAtAsc(
                    tenantId, RecommendationSourceType.KNOWLEDGE, sourceRef))) {
            Optional<RecommendationCard> card = recommendationCards.findByCardIdAndTenantId(source.cardId(), tenantId);
            if (card.isEmpty()) {
                continue;
            }
            Optional<RecommendationTrigger> trigger =
                recommendationTriggers.findByTriggerIdAndTenantId(card.get().triggerId(), tenantId);
            if (trigger.isEmpty() || isBlank(trigger.get().patientId())) {
                continue;
            }
            String targetRef = "patient:" + trigger.get().patientId()
                + "/encounter:" + safeSegment(trigger.get().encounterId())
                + "/card:" + card.get().cardId();
            saveAffectedTask(
                tenantId, invalidation, version, AffectedCaseTaskType.PHYSICIAN_REVIEW,
                AffectedCaseTargetType.PATIENT_CASE, targetRef, reason);
        }
    }

    private void createPatientPathwayTasks(String tenantId, KnowledgeInvalidation invalidation,
                                           KnowledgeAssetVersion version, String reason) {
        String sourceRef = canonicalKnowledgeVersionRef(version.id());
        for (PathwayTemplate template : safeList(pathwayTemplates.findByTenantIdAndSourceRef(tenantId, sourceRef))) {
            for (PatientPathway runtime : safeList(
                    patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(template.templateId(), tenantId))) {
                if (!ACTIVE_PATHWAY_STATUSES.contains(runtime.status())) {
                    continue;
                }
                String targetRef = "patient-pathway:" + runtime.patientPathwayId()
                    + "/template:" + template.templateId();
                saveAffectedTask(
                    tenantId, invalidation, version, AffectedCaseTaskType.PHYSICIAN_REVIEW,
                    AffectedCaseTargetType.PATIENT_PATHWAY, targetRef, reason);
            }
        }
    }

    private void saveAffectedTask(String tenantId, KnowledgeInvalidation invalidation, KnowledgeAssetVersion version,
                                  AffectedCaseTaskType taskType, AffectedCaseTargetType targetType,
                                  String targetRef, String reason) {
        String taskKey = affectedTaskKey(invalidation, taskType, targetRef);
        affectedTasks.findByTenantIdAndTaskKey(tenantId, taskKey)
            .orElseGet(() -> {
                Instant now = Instant.now();
                String actor = currentActor();
                return affectedTasks.save(new AffectedCaseTask(
                    null,
                    tenantId,
                    taskKey,
                    invalidation.id(),
                    version.identityId(),
                    version.id(),
                    taskType,
                    AffectedCaseTaskStatus.OPEN,
                    targetType,
                    targetRef,
                    normalizedReason(reason, invalidation.reason()),
                    now.plus(Duration.ofDays(1)),
                    actor,
                    RequestContext.currentTraceId(),
                    now,
                    actor,
                    now,
                    actor
                ));
            });
    }

    private String affectedTaskKey(KnowledgeInvalidation invalidation, AffectedCaseTaskType taskType, String targetRef) {
        return "knowledge-invalidation:" + invalidation.id() + ":" + taskType + ":" + targetRef;
    }

    private int countByTarget(List<AffectedCaseTask> tasks, AffectedCaseTargetType targetType) {
        return (int) tasks.stream()
            .filter(task -> task.targetType() == targetType)
            .count();
    }

    private String impactDigest(List<AffectedCaseTask> tasks) {
        String material = tasks.stream()
            .map(task -> safeSegment(task.taskKey()) + "|" + enumName(task.taskType()) + "|"
                + enumName(task.targetType()) + "|" + enumName(task.status()) + "|" + safeSegment(task.targetRef()))
            .sorted()
            .collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "安全撤回影响摘要计算失败", e);
        }
    }

    private String taskEvidenceLine(SafetyAffectedTaskResponse task) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("recordType", "task");
        line.put("taskKey", task.taskKey());
        line.put("taskType", task.taskType());
        line.put("targetType", task.targetType());
        line.put("targetRef", task.targetRef());
        line.put("status", task.status());
        line.put("dueAt", task.dueAt());
        return writeNdjson(line);
    }

    private String writeNdjson(Map<String, ?> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "安全撤回证据导出失败", e);
        }
    }

    private String canonicalKnowledgeVersionRef(Long versionId) {
        return "knowledge-version:" + versionId;
    }

    private String normalizedReason(String requestReason, String invalidationReason) {
        if (!isBlank(requestReason)) {
            return requestReason.trim();
        }
        if (!isBlank(invalidationReason)) {
            return invalidationReason.trim();
        }
        return "安全撤回影响复核";
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String safeSegment(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }

    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(actor -> !actor.isBlank())
            .orElse("system");
    }
}
