package com.medkernel.compliance.exportconfirmation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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
 * 敏感数据导出确认服务。
 */
@Service
public class ExportConfirmationService {

    private static final String TARGET_TYPE = "mk_compliance_export_confirmation";
    private static final String CONFIRMATION_EVIDENCE_TYPE = "COMPLIANCE_EXPORT_CONFIRMATION";
    private static final String EXPORT_EVIDENCE_TYPE = "COMPLIANCE_EXPORT";
    private static final int AUDIT_SUMMARY_MAX_LENGTH = 512;
    private static final int EVIDENCE_ID_MAX_LENGTH = 64;
    private static final int EVIDENCE_ID_HASH_LENGTH = 16;

    private final ExportConfirmationRepository repository;
    private final EvidenceService evidenceService;
    private final AuditRecorder auditRecorder;
    private final List<ExportArtifactProvider> artifactProviders;
    private final ObjectMapper objectMapper;

    public ExportConfirmationService(
            ExportConfirmationRepository repository,
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
    public PageResponse<ExportConfirmationResponse> listConfirmations(
            String tenantId,
            String resourceType,
            ExportConfirmationStatus status,
            PageRequest pageRequest) {
        String safeTenant = requireTenant(tenantId);
        PageRequest safePage = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String normalizedResource = resourceType == null || resourceType.isBlank()
            ? null
            : normalizeResourceType(resourceType);
        String normalizedStatus = status == null ? null : status.name();
        long total = repository.countByFilter(safeTenant, normalizedResource, normalizedStatus);
        if (total == 0L) {
            return PageResponse.empty(safePage);
        }
        List<ExportConfirmationResponse> confirmations = repository.pageByFilter(
                safeTenant,
                normalizedResource,
                normalizedStatus,
                safePage.offset(),
                safePage.safeSize()
            )
            .stream()
            .map(ExportConfirmationResponse::from)
            .toList();
        return PageResponse.of(confirmations, safePage, total);
    }

    @Transactional
    public ExportConfirmationResponse confirmExport(
            String tenantId,
            ExportConfirmationRequest request,
            String actor) {
        String safeTenant = requireTenant(tenantId);
        String idempotencyKey = normalizeToken(
            request.idempotencyKey(),
            "导出确认幂等键不能为空",
            128
        );
        var existing = repository.findByTenantIdAndIdempotencyKey(safeTenant, idempotencyKey);
        if (existing.isPresent()) {
            return ExportConfirmationResponse.from(existing.get());
        }

        String resourceType = normalizeResourceType(request.resourceType());
        String scopeSnapshot = serializeScope(request.exportScope());
        String safeActor = safeActor(actor);
        String reason = normalizeReason(request.reason(), "导出确认理由不能为空");
        Instant now = Instant.now();
        ExportConfirmation base = new ExportConfirmation(
            null,
            confirmationId(resourceType, idempotencyKey),
            safeTenant,
            resourceType,
            scopeSnapshot,
            idempotencyKey,
            reason,
            safeActor,
            now,
            ExportConfirmationStatus.CONFIRMED.name(),
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
            RequestContext.currentTraceId()
        );
        EvidenceResponse evidence = createConfirmationEvidence(safeTenant, base);
        ExportConfirmation saved = repository.save(new ExportConfirmation(
            base.id(),
            base.confirmationId(),
            base.tenantId(),
            base.resourceType(),
            base.exportScopeSnapshot(),
            base.idempotencyKey(),
            base.reason(),
            base.confirmedBy(),
            base.confirmedAt(),
            base.status(),
            base.exportUri(),
            base.exportDigest(),
            evidence.evidenceId(),
            evidence.fileUri(),
            base.exportEvidenceId(),
            base.exportEvidenceFileUri(),
            base.version(),
            base.createdAt(),
            base.createdBy(),
            base.updatedAt(),
            base.updatedBy(),
            base.traceId()
        ));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.CREATE,
            TARGET_TYPE,
            saved.confirmationId(),
            auditSummary("确认敏感数据导出", saved, reason),
            null,
            saved,
            null
        ));
        return ExportConfirmationResponse.from(saved);
    }

    @Transactional
    public ExportConfirmationResponse completeExportFromJob(
            String tenantId,
            String confirmationId,
            ExportJobCompletionRequest request,
            String actor) {
        String safeTenant = requireTenant(tenantId);
        ExportConfirmation current = findConfirmation(safeTenant, confirmationId);
        return completeConfirmedExport(safeTenant, current, request, actor);
    }

    private ExportConfirmationResponse completeConfirmedExport(
            String tenantId,
            ExportConfirmation current,
            ExportJobCompletionRequest request,
            String actor) {
        String safeActor = safeActor(actor);
        ExportConfirmationStatus status = ExportConfirmationStatus.valueOf(current.status());
        if (status == ExportConfirmationStatus.EXPORTED) {
            return ExportConfirmationResponse.from(current);
        }
        if (status != ExportConfirmationStatus.CONFIRMED) {
            throw ApiException.conflict("只有已确认的导出才能登记导出完成");
        }
        checkVersion(current, request.expectedVersion());
        ExportArtifact artifact = resolveProvider(current.resourceType()).completedExportArtifact(
            normalizeToken(request.jobId(), "导出任务 ID 不能为空", 128)
        );
        assertConfirmedArtifact(current, artifact);
        String exportDigest = normalizeDigest(artifact.exportDigest());
        EvidenceResponse evidence = createExportEvidence(
            tenantId,
            current,
            request,
            artifact,
            safeActor
        );
        Instant now = Instant.now();
        ExportConfirmation saved = repository.save(new ExportConfirmation(
            current.id(),
            current.confirmationId(),
            current.tenantId(),
            current.resourceType(),
            current.exportScopeSnapshot(),
            current.idempotencyKey(),
            current.reason(),
            current.confirmedBy(),
            current.confirmedAt(),
            ExportConfirmationStatus.EXPORTED.name(),
            artifact.downloadUri(),
            exportDigest,
            current.confirmationEvidenceId(),
            current.confirmationEvidenceFileUri(),
            evidence.evidenceId(),
            evidence.fileUri(),
            current.version() + 1L,
            current.createdAt(),
            current.createdBy(),
            now,
            safeActor,
            RequestContext.currentTraceId()
        ));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.EXPORT,
            TARGET_TYPE,
            saved.confirmationId(),
            auditSummary("登记敏感数据真实导出完成", saved, request.reason()),
            current,
            saved,
            null
        ));
        return ExportConfirmationResponse.from(saved);
    }

    /**
     * 使用导出确认与异步任务共享的幂等键登记真实文件完成。
     *
     * <p>后台任务无需携带确认表主键；任务成功后以租户和幂等键定位此前冻结的唯一范围。
     */
    @Transactional
    public ExportConfirmationResponse completeExportFromJobByIdempotencyKey(
            String tenantId,
            String idempotencyKey,
            String jobId,
            String reason,
            String actor) {
        String safeTenant = requireTenant(tenantId);
        String safeIdempotencyKey = normalizeToken(
            idempotencyKey,
            "导出任务幂等键不能为空",
            128
        );
        ExportConfirmation confirmation = repository
            .findByTenantIdAndIdempotencyKey(safeTenant, safeIdempotencyKey)
            .orElseThrow(() -> ApiException.forbidden("导出任务没有对应的范围确认"));
        return completeConfirmedExport(
            safeTenant,
            confirmation,
            new ExportJobCompletionRequest(jobId, reason, null),
            actor
        );
    }

    private ExportConfirmation findConfirmation(String tenantId, String confirmationId) {
        return repository.findByTenantIdAndConfirmationId(
                tenantId,
                normalizeToken(confirmationId, "导出确认 ID 不能为空", 128)
            )
            .orElseThrow(() -> ApiException.notFound("导出确认记录"));
    }

    private EvidenceResponse createConfirmationEvidence(
            String tenantId,
            ExportConfirmation confirmation) {
        return evidenceService.createSnapshot(tenantId, new EvidenceCreateDto(
            evidenceId(confirmation.confirmationId(), "confirmation"),
            RequestContext.currentTraceId(),
            CONFIRMATION_EVIDENCE_TYPE,
            "CONFIRM",
            TARGET_TYPE,
            confirmation.confirmationId(),
            "敏感数据导出范围确认",
            payload(Map.of(
                "confirmationId", confirmation.confirmationId(),
                "resourceType", confirmation.resourceType(),
                "exportScopeSnapshot", confirmation.exportScopeSnapshot(),
                "reason", confirmation.reason(),
                "confirmedBy", confirmation.confirmedBy()
            ))
        ));
    }

    private EvidenceResponse createExportEvidence(
            String tenantId,
            ExportConfirmation confirmation,
            ExportJobCompletionRequest request,
            ExportArtifact artifact,
            String actor) {
        return evidenceService.createSnapshot(tenantId, new EvidenceCreateDto(
            evidenceId(confirmation.confirmationId(), "export"),
            RequestContext.currentTraceId(),
            EXPORT_EVIDENCE_TYPE,
            AuditAction.EXPORT.name(),
            TARGET_TYPE,
            confirmation.confirmationId(),
            "敏感数据真实导出完成登记",
            payload(Map.of(
                "confirmationId", confirmation.confirmationId(),
                "resourceType", confirmation.resourceType(),
                "exportScopeSnapshot", confirmation.exportScopeSnapshot(),
                "jobId", artifact.jobId(),
                "exportUri", artifact.downloadUri(),
                "exportDigest", artifact.exportDigest(),
                "operatorId", actor,
                "reason", request.reason()
            ))
        ));
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

    private void assertConfirmedArtifact(
            ExportConfirmation confirmation,
            ExportArtifact artifact) {
        if (!confirmation.resourceType().equals(normalizeResourceType(artifact.resourceType()))) {
            throw ApiException.conflict("导出任务资源类型与确认记录不一致");
        }
        if (!confirmation.idempotencyKey().equals(artifact.idempotencyKey())) {
            throw ApiException.conflict("导出任务幂等键与确认记录不一致");
        }
        try {
            if (!objectMapper.readTree(confirmation.exportScopeSnapshot())
                    .equals(objectMapper.readTree(artifact.requestSnapshot()))) {
                throw ApiException.conflict("导出任务范围与确认记录不一致");
            }
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出确认范围快照不是合法 JSON");
        }
    }

    private String payload(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出确认证据快照序列化失败");
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

    private void checkVersion(ExportConfirmation current, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(current.version())) {
            throw ApiException.conflict("导出确认版本冲突");
        }
    }

    private String confirmationId(String resourceType, String idempotencyKey) {
        return "exp-" + resourceType.replace('_', '-') + "-"
            + idempotencyKey.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String evidenceId(String confirmationId, String suffix) {
        String raw = "evd-" + confirmationId + "-" + suffix;
        String safe = raw
            .replaceAll("[^A-Za-z0-9_-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (safe.length() <= EVIDENCE_ID_MAX_LENGTH) {
            return safe;
        }
        String digest = Sha256ContentHash.sha256(safe, "导出确认证据 ID 源材料不能为空")
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
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "导出确认标识只允许字母、数字、点、下划线和连字符"
            );
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

    private String auditSummary(
            String prefix,
            ExportConfirmation confirmation,
            String reason) {
        String summary = prefix + "：" + confirmation.resourceType() + "/"
            + confirmation.confirmationId();
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
