package com.medkernel.engine.cdss.risk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.medkernel.engine.cdshook.CdsHookContract;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPT-03 CDSS/器械风险分级矩阵服务。
 */
@Service
public class CdssRiskMatrixService {

    static final String ASSET_IDENTITY = "CDSS.RISK.MATRIX";
    private static final String SCHEMA_VERSION = "1.0";

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final CdssRiskMatrixRepository matrixRepository;
    private final AuditRecorder auditRecorder;
    private final AssetVersionService versionService;
    private final ReleasePort releasePort;
    private final RuntimeReleaseCdssRiskMatrixSelector runtimeSelector;

    public CdssRiskMatrixService(
            CdssRiskMatrixRepository matrixRepository,
            AuditRecorder auditRecorder,
            AssetVersionService versionService,
            ReleasePort releasePort,
            RuntimeReleaseCdssRiskMatrixSelector runtimeSelector) {
        this.matrixRepository = matrixRepository;
        this.auditRecorder = auditRecorder;
        this.versionService = versionService;
        this.releasePort = releasePort;
        this.runtimeSelector = runtimeSelector;
    }

    @Transactional(readOnly = true)
    public CdssRiskAssessment assess(
            String runtimeReleaseId,
            String triggerPoint,
            RecommendationRiskLevel severityLevel,
            CdssAutomationLevel automationLevel) {
        String normalizedTrigger = normalizeTrigger(triggerPoint);
        RecommendationRiskLevel severity = severityLevel == null ? RecommendationRiskLevel.LOW : severityLevel;
        CdssAutomationLevel automation = automationLevel == null ? CdssAutomationLevel.INFORM_ONLY : automationLevel;
        return runtimeSelector.selectRule(
                tenantId(), requireText(runtimeReleaseId, "机构生效版本不能为空"),
                normalizedTrigger, severity, automation)
            .map(CdssRiskMatrixRule::toAssessment)
            .orElseGet(() -> builtInBaseline(normalizedTrigger, severity, automation));
    }

    @Transactional(readOnly = true)
    public CdssRiskMatrixResponse activeMatrix() {
        LinkedHashMap<String, CdssRiskMatrixRule> latestByScope = new LinkedHashMap<>();
        matrixRepository.findByTenantIdAndStatusOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                tenantId(), CdssRiskMatrixStatus.ACTIVE)
            .stream()
            .sorted(Comparator
                .comparing(CdssRiskMatrixRule::triggerPoint)
                .thenComparing(rule -> rule.severityLevel().name())
                .thenComparing(rule -> rule.automationLevel().name())
                .thenComparing(CdssRiskMatrixRule::updatedAt, Comparator.reverseOrder()))
            .forEach(rule -> latestByScope.putIfAbsent(scopeKey(rule), rule));
        return new CdssRiskMatrixResponse(List.copyOf(latestByScope.values()), traceId());
    }

    @Transactional
    public CdssRiskMatrixResponse updateMatrix(CdssRiskMatrixUpdateRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵变更请求不能为空");
        }
        if (request.entries().isEmpty()) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵至少包含一条规则");
        }
        String matrixVersion = requireText(request.matrixVersion(), "风险矩阵版本不能为空");
        String changeReason = requireText(request.changeReason(), "风险矩阵变更原因不能为空");
        CdssRiskMatrixStatus status = normalizeStatus(request.status());
        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();
        List<CdssRiskMatrixRule> saved = new ArrayList<>();
        List<ValidatedMatrixEntry> validatedEntries = new ArrayList<>();
        Set<String> requestScopes = new HashSet<>();
        for (CdssRiskMatrixEntryRequest entry : request.entries()) {
            if (entry == null) {
                throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵规则不能为空");
            }
            String triggerPoint = normalizeTrigger(entry.triggerPoint());
            String scope = triggerPoint + "|" + entry.severityLevel() + "|" + entry.automationLevel();
            if (!requestScopes.add(scope)) {
                throw new ApiException(ErrorCode.ENG_REC_001, "同一矩阵版本内风险规则维度不能重复");
            }
            validateAgainstSafetyBaseline(triggerPoint, entry);
            validatedEntries.add(new ValidatedMatrixEntry(triggerPoint, entry));
        }
        for (ValidatedMatrixEntry validatedEntry : validatedEntries) {
            CdssRiskMatrixEntryRequest entry = validatedEntry.entry();
            saved.add(matrixRepository.save(new CdssRiskMatrixRule(
                null,
                "crm-" + UUID.randomUUID(),
                tenantId,
                validatedEntry.triggerPoint(),
                entry.severityLevel(),
                entry.automationLevel(),
                entry.riskLevel(),
                entry.reviewRequirement(),
                entry.silentRunHours(),
                entry.releaseGate(),
                entry.autoExecutionAllowed(),
                hasText(entry.samdClassification()) ? entry.samdClassification().trim() : "NMPA_RESERVED",
                hasText(entry.regulatoryEvidence()) ? entry.regulatoryEvidence().trim() : "NOT_ASSESSED",
                status,
                matrixVersion,
                requireText(entry.explanation(), "风险矩阵规则解释不能为空"),
                now,
                actor,
                now,
                actor,
                traceId)));
        }
        saved.sort(Comparator
            .comparing(CdssRiskMatrixRule::triggerPoint)
            .thenComparing(rule -> rule.severityLevel().name())
            .thenComparing(rule -> rule.automationLevel().name()));
        AssetVersion assetVersion = registerUnifiedAssetVersion(
            tenantId, request, saved, actor, traceId);
        publishUnifiedAssetWhenEffective(status, assetVersion, request.changeReason(), actor, traceId);
        auditRecorder.record(AuditAction.UPDATE, "mk_engine_cdss_risk_matrix", matrixVersion,
            "更新 CDSS 风险分级矩阵(" + status + ") " + changeReason);
        return new CdssRiskMatrixResponse(saved, traceId);
    }

    private record ValidatedMatrixEntry(String triggerPoint, CdssRiskMatrixEntryRequest entry) {}

    private AssetVersion registerUnifiedAssetVersion(
            String tenantId,
            CdssRiskMatrixUpdateRequest request,
            List<CdssRiskMatrixRule> saved,
            String actor,
            String traceId) {
        String matrixVersion = request.matrixVersion().trim();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", SCHEMA_VERSION);
        content.put("matrixVersion", matrixVersion);
        content.put("status", request.status().name());
        content.put("changeReason", request.changeReason().trim());
        content.put("rules", saved.stream().map(this::assetRule).toList());
        return versionService.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.CDSS_RISK,
            ASSET_IDENTITY,
            null,
            "ALL",
            writeContent(content),
            null,
            "cdss-risk-matrix:" + matrixVersion,
            actor,
            traceId,
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED
        ));
    }

    private Map<String, Object> assetRule(CdssRiskMatrixRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("matrixId", rule.matrixId());
        value.put("triggerPoint", rule.triggerPoint());
        value.put("severityLevel", rule.severityLevel());
        value.put("automationLevel", rule.automationLevel());
        value.put("riskLevel", rule.riskLevel());
        value.put("reviewRequirement", rule.reviewRequirement());
        value.put("silentRunHours", rule.silentRunHours());
        value.put("releaseGate", rule.releaseGate());
        value.put("autoExecutionAllowed", rule.autoExecutionAllowed());
        value.put("samdClassification", rule.samdClassification());
        value.put("regulatoryEvidence", rule.regulatoryEvidence());
        value.put("explanation", rule.explanation());
        return value;
    }

    private void publishUnifiedAssetWhenEffective(
            CdssRiskMatrixStatus status,
            AssetVersion assetVersion,
            String changeReason,
            String actor,
            String traceId) {
        if (status != CdssRiskMatrixStatus.PUBLISHED && status != CdssRiskMatrixStatus.ACTIVE) {
            return;
        }
        releasePort.publish(new VersionReleaseCommand(
            assetVersion.tenantId(),
            VersionedAssetType.CDSS_RISK,
            assetVersion.assetIdentity(),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            assetVersion.applicableScope(),
            VersionReleaseScopeType.ALL,
            null,
            RolloutPolicy.all(),
            "CDSS 风险矩阵变更：" + requireText(changeReason, "风险矩阵变更原因不能为空"),
            null,
            actor,
            traceId,
            null
        ));
    }

    private String writeContent(Map<String, Object> content) {
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "CDSS 风险矩阵资产正文序列化失败", exception);
        }
    }

    private void validateAgainstSafetyBaseline(String triggerPoint, CdssRiskMatrixEntryRequest entry) {
        if (entry.severityLevel() == null
                || entry.automationLevel() == null
                || entry.riskLevel() == null
                || entry.reviewRequirement() == null) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵规则维度和门槛不能为空");
        }
        if (!hasText(entry.releaseGate())) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵上线门槛不能为空");
        }
        CdssRiskAssessment baseline = builtInBaseline(
            triggerPoint, entry.severityLevel(), entry.automationLevel());
        if (riskOrder(entry.riskLevel()) < riskOrder(baseline.riskLevel())) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵不能低于内置医疗安全基线");
        }
        if (reviewOrder(entry.reviewRequirement()) < reviewOrder(baseline.reviewRequirement())) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵审核强度不能低于内置医疗安全基线");
        }
        if (entry.silentRunHours() < baseline.silentRunHours()) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵静默试运行时长不能低于内置医疗安全基线");
        }
        if (entry.autoExecutionAllowed() && !baseline.autoExecutionAllowed()) {
            throw new ApiException(ErrorCode.ENG_REC_001, "当前维度不允许配置自动执行");
        }
        if ((entry.riskLevel() == RecommendationRiskLevel.HIGH
                || entry.riskLevel() == RecommendationRiskLevel.CRITICAL)
                && entry.reviewRequirement() == CdssReviewRequirement.OPTIONAL_REVIEW) {
            throw new ApiException(ErrorCode.ENG_REC_001, "高风险 CDSS 必须绑定人工审核门槛");
        }
    }

    private CdssRiskAssessment builtInBaseline(
            String triggerPoint,
            RecommendationRiskLevel severity,
            CdssAutomationLevel automationLevel) {
        if (automationLevel == CdssAutomationLevel.AUTOMATED) {
            return new CdssRiskAssessment(
                "builtin-risk-baseline", "baseline", RecommendationRiskLevel.CRITICAL,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 168, "OPT04_REDLINE_SILENT_TRIAL",
                false, "NMPA_RESERVED", "RISK_ANALYSIS_REQUIRED",
                "自动化 CDSS 输出按医疗安全基线提升为红线级，禁止自动执行");
        }
        if (severity == RecommendationRiskLevel.CRITICAL) {
            return new CdssRiskAssessment(
                "builtin-risk-baseline", "baseline", RecommendationRiskLevel.CRITICAL,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 168, "OPT04_REDLINE_SILENT_TRIAL",
                false, "NMPA_RESERVED", "RISK_ANALYSIS_REQUIRED",
                "红线级 CDSS 输出必须由医师逐次确认并经过静默试运行门槛");
        }
        if (severity == RecommendationRiskLevel.HIGH
                || (triggerHazard(triggerPoint) >= 2 && severity == RecommendationRiskLevel.MEDIUM
                    && automationLevel == CdssAutomationLevel.INTERRUPTIVE)) {
            return new CdssRiskAssessment(
                "builtin-risk-baseline", "baseline", RecommendationRiskLevel.HIGH,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
                false, "NMPA_RESERVED", "TRACEABLE_EVIDENCE_REQUIRED",
                "高危或高危害触发点的打断式 CDSS 输出必须医师确认");
        }
        if (severity == RecommendationRiskLevel.MEDIUM) {
            return new CdssRiskAssessment(
                "builtin-risk-baseline", "baseline", RecommendationRiskLevel.MEDIUM,
                CdssReviewRequirement.OPTIONAL_REVIEW, 24, "STANDARD_CHANGE_REVIEW",
                false, "NMPA_RESERVED", "NOT_ASSESSED",
                "中风险 CDSS 输出保留标准变更审查与追溯字段");
        }
        return new CdssRiskAssessment(
            "builtin-risk-baseline", "baseline", RecommendationRiskLevel.LOW,
            CdssReviewRequirement.OPTIONAL_REVIEW, 0, "STANDARD_CHANGE_REVIEW",
            false, "NMPA_RESERVED", "NOT_ASSESSED",
            "低风险 CDSS 信息提示仅允许人工参考");
    }

    private int triggerHazard(String triggerPoint) {
        return switch (triggerPoint) {
            case "order-sign", "medication-prescribe", "discharge-sign" -> 2;
            case "result-review", "followup-alert" -> 1;
            default -> 0;
        };
    }

    private int riskOrder(RecommendationRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case CRITICAL -> 3;
        };
    }

    private int reviewOrder(CdssReviewRequirement reviewRequirement) {
        return switch (reviewRequirement) {
            case OPTIONAL_REVIEW -> 0;
            case PHYSICIAN_CONFIRMATION -> 1;
        };
    }

    private String scopeKey(CdssRiskMatrixRule rule) {
        return rule.triggerPoint() + "|" + rule.severityLevel() + "|" + rule.automationLevel();
    }

    private CdssRiskMatrixStatus normalizeStatus(CdssRiskMatrixStatus status) {
        CdssRiskMatrixStatus normalized = status == null ? CdssRiskMatrixStatus.ACTIVE : status;
        if (normalized == CdssRiskMatrixStatus.RETIRED) {
            throw new ApiException(ErrorCode.ENG_REC_001, "风险矩阵不能通过受控更新直接创建退役状态");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ApiException(ErrorCode.ENG_REC_001, message);
        }
        return value.trim();
    }

    private String normalizeTrigger(String triggerPoint) {
        ClinicalEventTriggerPoint supported = CdsHookContract.requireSupportedHook(triggerPoint);
        return supported.wireValue();
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
