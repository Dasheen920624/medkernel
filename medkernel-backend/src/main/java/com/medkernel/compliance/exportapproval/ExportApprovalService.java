package com.medkernel.compliance.exportapproval;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.export.ExportArtifact;
import com.medkernel.shared.export.ExportArtifactProvider;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * SYS-06 敏感数据导出审批服务。
 */
@Service
public class ExportApprovalService {

    private static final String TARGET_TYPE = "mk_compliance_export_approval";
    private static final String APPROVAL_EVIDENCE_TYPE = "COMPLIANCE_EXPORT_APPROVAL";
    private static final String EXPORT_EVIDENCE_TYPE = "COMPLIANCE_EXPORT";
    private static final int AUDIT_SUMMARY_MAX_LENGTH = 512;
    private static final int EVIDENCE_ID_MAX_LENGTH = 64;
    private static final int EVIDENCE_ID_HASH_LENGTH = 16;

    private final ExportApprovalRepository repository;
    private final EvidenceService evidenceService;
    private final AuditRecorder auditRecorder;
    private final List<ExportArtifactProvider> artifactProviders;
    private final ObjectMapper objectMapper;

    public ExportApprovalService(
            ExportApprovalRepository repository,
            EvidenceService evidenceService,
            AuditRecorder auditRecorder,
            List<ExportArtifactProvider> artifactProviders,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.evidenceService = evidenceService;
        this.auditRecorder = auditRecorder;
        this.artifactProviders = List.copyOf(artifactProviders);
        this.objectMapper = objectMapper.copy()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public List<ExportApprovalResponse> listApprovals(
            String tenantId, String resourceType, ExportApprovalStatus status) {
        String safeTenant = requireTenant(tenantId);
        String normalizedResource = resourceType == null || resourceType.isBlank()
            ? null
            : normalizeResourceType(resourceType);
        String normalizedStatus = status == null ? null : status.name();

        List<ExportApproval> approvals;
        if (normalizedResource != null && normalizedStatus != null) {
            approvals = repository.findByTenantIdAndResourceTypeAndStatusOrderByRequestedAtDesc(
                safeTenant, normalizedResource, normalizedStatus);
        } else if (normalizedResource != null) {
            approvals = repository.findByTenantIdAndResourceTypeOrderByRequestedAtDesc(
                safeTenant, normalizedResource);
        } else if (normalizedStatus != null) {
            approvals = repository.findByTenantIdAndStatusOrderByRequestedAtDesc(
                safeTenant, normalizedStatus);
        } else {
            approvals = repository.findByTenantIdOrderByRequestedAtDesc(safeTenant);
        }
        return approvals.stream().map(ExportApprovalResponse::from).toList();
    }

    @Transactional
    public ExportApprovalResponse requestExport(String tenantId, ExportApprovalRequest request, String actor) {
        String safeTenant = requireTenant(tenantId);
        String idempotencyKey = normalizeToken(request.idempotencyKey(), "导出审批幂等键不能为空", 128);
        var existing = repository.findByTenantIdAndIdempotencyKey(safeTenant, idempotencyKey);
        if (existing.isPresent()) {
            return ExportApprovalResponse.from(existing.get());
        }

        String resourceType = normalizeResourceType(request.resourceType());
        String scopeSnapshot = serializeScope(request.exportScope());
        String safeActor = safeActor(actor);
        Instant now = Instant.now();
        ExportApproval saved = repository.save(new ExportApproval(
            null,
            approvalId(resourceType, idempotencyKey),
            safeTenant,
            resourceType,
            scopeSnapshot,
            idempotencyKey,
            normalizeReason(request.reason(), "导出申请理由不能为空"),
            safeActor,
            now,
            ExportApprovalStatus.REQUESTED.name(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1L,
            now,
            safeActor,
            now,
            safeActor,
            RequestContext.currentTraceId()));

        auditRecorder.record(new AuditRecordCommand(
            AuditAction.CREATE,
            TARGET_TYPE,
            saved.approvalId(),
            auditSummary("创建敏感数据导出申请", saved, request.reason()),
            null,
            saved,
            null));
        return ExportApprovalResponse.from(saved);
    }

    @Transactional
    public ExportApprovalResponse reviewExport(
            String tenantId, String approvalId, ExportApprovalReviewRequest request, String actor) {
        String safeTenant = requireTenant(tenantId);
        String safeActor = safeActor(actor);
        ExportApproval current = findApproval(safeTenant, approvalId);
        ExportApprovalStatus status = ExportApprovalStatus.valueOf(current.status());
        ExportApprovalDecision decision = request.decision();

        if (isTerminalReview(status)) {
            return idempotentReviewOrConflict(current, decision);
        }
        if (status != ExportApprovalStatus.REQUESTED) {
            throw ApiException.conflict("当前导出审批状态不允许审批：" + status);
        }
        checkVersion(current, request.expectedVersion());
        if (safeActor.equals(current.requestedBy())) {
            throw ApiException.forbidden("申请人与审批人不能相同");
        }

        EvidenceResponse evidence = createReviewEvidence(safeTenant, current, request, safeActor);
        Instant now = Instant.now();
        ExportApprovalStatus nextStatus = decision == ExportApprovalDecision.APPROVE
            ? ExportApprovalStatus.APPROVED
            : ExportApprovalStatus.REJECTED;
        ExportApproval saved = repository.save(new ExportApproval(
            current.id(),
            current.approvalId(),
            current.tenantId(),
            current.resourceType(),
            current.exportScopeSnapshot(),
            current.idempotencyKey(),
            current.requestReason(),
            current.requestedBy(),
            current.requestedAt(),
            nextStatus.name(),
            safeActor,
            decision.name(),
            normalizeReason(request.comment(), "审批意见不能为空"),
            now,
            current.exportUri(),
            current.exportDigest(),
            evidence.evidenceId(),
            evidence.fileUri(),
            current.exportEvidenceId(),
            current.exportEvidenceFileUri(),
            current.version() + 1L,
            current.createdAt(),
            current.createdBy(),
            now,
            safeActor,
            RequestContext.currentTraceId()));

        auditRecorder.record(new AuditRecordCommand(
            AuditAction.REVIEW,
            TARGET_TYPE,
            saved.approvalId(),
            auditSummary("审批敏感数据导出申请", saved, request.comment()),
            current,
            saved,
            null));
        return ExportApprovalResponse.from(saved);
    }

    @Transactional
    public ExportApprovalResponse completeExportFromJob(
            String tenantId, String approvalId, ExportJobCompletionRequest request, String actor) {
        String safeTenant = requireTenant(tenantId);
        String safeActor = safeActor(actor);
        ExportApproval current = findApproval(safeTenant, approvalId);
        ExportApprovalStatus status = ExportApprovalStatus.valueOf(current.status());
        if (status == ExportApprovalStatus.EXPORTED) {
            return ExportApprovalResponse.from(current);
        }
        if (status != ExportApprovalStatus.APPROVED) {
            throw ApiException.conflict("只有审批通过的导出申请才能登记导出完成");
        }
        checkVersion(current, request.expectedVersion());
        ExportArtifact artifact = resolveProvider(current.resourceType()).completedExportArtifact(
            normalizeToken(request.jobId(), "导出任务 ID 不能为空", 128));
        assertApprovedArtifact(current, artifact);
        String exportUri = artifact.downloadUri();
        String exportDigest = normalizeDigest(artifact.exportDigest());

        EvidenceResponse evidence = createExportEvidence(safeTenant, current, request, artifact, safeActor);
        Instant now = Instant.now();
        ExportApproval saved = repository.save(new ExportApproval(
            current.id(),
            current.approvalId(),
            current.tenantId(),
            current.resourceType(),
            current.exportScopeSnapshot(),
            current.idempotencyKey(),
            current.requestReason(),
            current.requestedBy(),
            current.requestedAt(),
            ExportApprovalStatus.EXPORTED.name(),
            current.reviewerId(),
            current.reviewDecision(),
            current.reviewComment(),
            current.reviewedAt(),
            exportUri,
            exportDigest,
            current.approvalEvidenceId(),
            current.approvalEvidenceFileUri(),
            evidence.evidenceId(),
            evidence.fileUri(),
            current.version() + 1L,
            current.createdAt(),
            current.createdBy(),
            now,
            safeActor,
            RequestContext.currentTraceId()));

        auditRecorder.record(new AuditRecordCommand(
            AuditAction.EXPORT,
            TARGET_TYPE,
            saved.approvalId(),
            auditSummary("登记敏感数据真实导出完成", saved, request.reason()),
            current,
            saved,
            null));
        return ExportApprovalResponse.from(saved);
    }

    private ExportApproval findApproval(String tenantId, String approvalId) {
        return repository.findByTenantIdAndApprovalId(tenantId, normalizeToken(approvalId, "导出审批 ID 不能为空", 128))
            .orElseThrow(() -> ApiException.notFound("导出审批申请"));
    }

    private ExportApprovalResponse idempotentReviewOrConflict(
            ExportApproval current, ExportApprovalDecision decision) {
        if (decision == ExportApprovalDecision.APPROVE
                && (ExportApprovalStatus.APPROVED.name().equals(current.status())
                    || ExportApprovalStatus.EXPORTED.name().equals(current.status()))) {
            return ExportApprovalResponse.from(current);
        }
        if (decision == ExportApprovalDecision.REJECT
                && ExportApprovalStatus.REJECTED.name().equals(current.status())) {
            return ExportApprovalResponse.from(current);
        }
        throw ApiException.conflict("导出审批已完成，不能更改审批结论");
    }

    private boolean isTerminalReview(ExportApprovalStatus status) {
        return status == ExportApprovalStatus.APPROVED
            || status == ExportApprovalStatus.REJECTED
            || status == ExportApprovalStatus.EXPORTED;
    }

    private EvidenceResponse createReviewEvidence(
            String tenantId, ExportApproval approval, ExportApprovalReviewRequest request, String actor) {
        return evidenceService.createSnapshot(tenantId, new EvidenceCreateDto(
            evidenceId(approval.approvalId(), "approval"),
            RequestContext.currentTraceId(),
            APPROVAL_EVIDENCE_TYPE,
            AuditAction.REVIEW.name(),
            TARGET_TYPE,
            approval.approvalId(),
            "敏感数据导出审批：" + request.decision().name(),
            payload(Map.of(
                "approvalId", approval.approvalId(),
                "resourceType", approval.resourceType(),
                "exportScopeSnapshot", approval.exportScopeSnapshot(),
                "decision", request.decision().name(),
                "comment", request.comment(),
                "requestedBy", approval.requestedBy(),
                "reviewerId", actor))));
    }

    private EvidenceResponse createExportEvidence(
            String tenantId,
            ExportApproval approval,
            ExportJobCompletionRequest request,
            ExportArtifact artifact,
            String actor) {
        return evidenceService.createSnapshot(tenantId, new EvidenceCreateDto(
            evidenceId(approval.approvalId(), "export"),
            RequestContext.currentTraceId(),
            EXPORT_EVIDENCE_TYPE,
            AuditAction.EXPORT.name(),
            TARGET_TYPE,
            approval.approvalId(),
            "敏感数据真实导出完成登记",
            payload(Map.of(
                "approvalId", approval.approvalId(),
                "resourceType", approval.resourceType(),
                "exportScopeSnapshot", approval.exportScopeSnapshot(),
                "jobId", artifact.jobId(),
                "exportUri", artifact.downloadUri(),
                "exportDigest", artifact.exportDigest(),
                "operatorId", actor,
                "reason", request.reason()))));
    }

    private ExportArtifactProvider resolveProvider(String resourceType) {
        List<ExportArtifactProvider> matches = artifactProviders.stream()
            .filter(provider -> provider.supports(resourceType))
            .toList();
        if (matches.isEmpty()) {
            throw ApiException.conflict("没有导出产物来源支持资源类型：" + resourceType);
        }
        if (matches.size() > 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "资源类型存在多个导出产物来源：" + resourceType);
        }
        return matches.getFirst();
    }

    private void assertApprovedArtifact(ExportApproval approval, ExportArtifact artifact) {
        if (!approval.resourceType().equals(normalizeResourceType(artifact.resourceType()))) {
            throw ApiException.conflict("导出任务资源类型与审批申请不一致");
        }
        if (!approval.idempotencyKey().equals(artifact.idempotencyKey())) {
            throw ApiException.conflict("导出任务幂等键与审批申请不一致");
        }
        try {
            if (!objectMapper.readTree(approval.exportScopeSnapshot())
                    .equals(objectMapper.readTree(artifact.requestSnapshot()))) {
                throw ApiException.conflict("导出任务审批范围与申请不一致");
            }
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出审批范围快照不是合法 JSON");
        }
    }

    private String payload(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出审批证据快照序列化失败");
        }
    }

    private String serializeScope(Map<String, Object> exportScope) {
        if (exportScope == null || exportScope.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出范围不能为空");
        }
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(exportScope));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出范围必须是可序列化结构");
        }
    }

    private void checkVersion(ExportApproval current, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(current.version())) {
            throw ApiException.conflict("导出审批版本冲突");
        }
    }

    private String approvalId(String resourceType, String idempotencyKey) {
        return "exp-" + resourceType.replace('_', '-') + "-"
            + idempotencyKey.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String evidenceId(String approvalId, String suffix) {
        String raw = "evd-" + approvalId + "-" + suffix;
        String safe = raw
            .replaceAll("[^A-Za-z0-9_-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (safe.length() <= EVIDENCE_ID_MAX_LENGTH) {
            return safe;
        }
        String digest = Sha256ContentHash.sha256(safe, "导出审批证据 ID 源材料不能为空")
            .substring(0, EVIDENCE_ID_HASH_LENGTH);
        String evidenceSuffix = "-" + suffix;
        int prefixLength = EVIDENCE_ID_MAX_LENGTH
            - 1
            - EVIDENCE_ID_HASH_LENGTH
            - evidenceSuffix.length();
        return safe.substring(0, prefixLength).replaceAll("-+$", "")
            + "-"
            + digest
            + evidenceSuffix;
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出资源类型不能为空");
        }
        if (normalized.length() > 128) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出资源类型过长");
        }
        return normalized;
    }

    private String normalizeToken(String value, String blankMessage, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, blankMessage);
        }
        if (normalized.length() > maxLength || !normalized.matches("[A-Za-z0-9._-]+")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出审批标识只允许字母、数字、点、下划线和连字符");
        }
        return normalized;
    }

    private String normalizeReason(String value, String blankMessage) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, blankMessage);
        }
        return normalized;
    }

    private String normalizeDigest(String value) {
        String normalized = normalizeReason(value, "导出文件摘要不能为空");
        if (!normalized.matches("sm3:[0-9a-fA-F]{64}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出文件摘要必须是 sm3: 加 64 位十六进制");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String auditSummary(String prefix, ExportApproval approval, String reason) {
        String summary = prefix + "：" + approval.resourceType() + "/" + approval.approvalId();
        if (reason != null && !reason.isBlank()) {
            summary = summary + "；原因：" + reason.trim().replaceAll("\\s+", " ");
        }
        if (summary.length() <= AUDIT_SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, AUDIT_SUMMARY_MAX_LENGTH - 3) + "...";
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId.trim();
    }

    private String safeActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return RequestContext.currentUserId().orElse("system");
    }
}
