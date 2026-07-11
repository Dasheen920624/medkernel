package com.medkernel.engine.safety;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixRepository;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixRule;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixStatus;
import com.medkernel.engine.context.ContextFieldPathPolicy;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.context.OrgScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPT-04 临床安全红线目录服务。
 *
 * <p>红线内容只来自数据库 / 知识配置。空库返回 NOT_CONFIGURED，不内置任何医学常量。
 */
@Service
public class ClinicalRedlineService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final ClinicalRedlineRepository repository;
    private final ClinicalRedlineTrialRepository trialRepository;
    private final AuditRecorder auditRecorder;
    private final AssetVersionService versionService;
    private final ReleasePort releasePort;
    private final CdssRiskMatrixRepository riskMatrices;

    public ClinicalRedlineService(
            ClinicalRedlineRepository repository,
            ClinicalRedlineTrialRepository trialRepository,
            AuditRecorder auditRecorder,
            AssetVersionService versionService,
            ReleasePort releasePort,
            CdssRiskMatrixRepository riskMatrices) {
        this.repository = repository;
        this.trialRepository = trialRepository;
        this.auditRecorder = auditRecorder;
        this.versionService = versionService;
        this.releasePort = releasePort;
        this.riskMatrices = riskMatrices;
    }

    @Transactional(readOnly = true)
    public ClinicalRedlineCatalogResponse activeCatalog(ClinicalRedlineCategory category) {
        String tenantId = tenantId();
        List<ClinicalRedlineRule> rows = category == null
            ? repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                tenantId, ClinicalRedlineStatus.ACTIVE)
            : repository.findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                tenantId, category, ClinicalRedlineStatus.ACTIVE);
        List<ClinicalRedlineResponse> redlines = rows.stream()
            .sorted(Comparator
                .comparing((ClinicalRedlineRule row) -> row.category().name())
                .thenComparing(ClinicalRedlineRule::redlineKey)
                .thenComparing(ClinicalRedlineRule::updatedAt, Comparator.reverseOrder()))
            .map(ClinicalRedlineRule::toResponse)
            .toList();
        ClinicalRedlineContentStatus contentStatus = redlines.isEmpty()
            ? ClinicalRedlineContentStatus.NOT_CONFIGURED
            : ClinicalRedlineContentStatus.CONFIGURED;
        return new ClinicalRedlineCatalogResponse(
            contentStatus,
            ClinicalRedlineCategory.requiredSafetyCategories(),
            redlines,
            RequestContext.currentTraceId());
    }

    @Transactional
    public ClinicalRedlineResponse createDraft(ClinicalRedlineDraftRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线草稿请求不能为空");
        }
        requireValidDraftPayload(request);
        String tenantId = tenantId();
        RedlineScope scope = validateScope(tenantId, request.scopeType(), request.scopeRef());
        String redlineId = requireText(request.redlineId(), "红线 ID 不能为空");
        String redlineKey = requireText(request.redlineKey(), "红线编码不能为空");
        String redlineVersion = requireText(request.redlineVersion(), "红线版本不能为空");
        ClinicalRedlineRule draftRule = new ClinicalRedlineRule(
            null,
            redlineId,
            tenantId,
            request.category(),
            requireText(request.triggerPoint(), "红线触发点不能为空"),
            scope.scopeType(),
            scope.scopeRef(),
            null,
            redlineKey,
            redlineVersion,
            ClinicalRedlineStatus.DRAFT,
            request.hazardSeverity(),
            requireText(request.riskMatrixId(), "风险矩阵 ID 不能为空"),
            requireText(request.riskMatrixVersion(), "风险矩阵版本不能为空"),
            request.reviewRequirement(),
            request.silentRunHours(),
            requireText(request.releaseGate(), "上线门槛不能为空"),
            requireText(request.title(), "红线标题不能为空"),
            requireText(request.clinicalHazard(), "红线危害分析不能为空"),
            canonicalConditionDsl(request.conditionDsl()),
            requireText(request.evidenceSource(), "红线证据来源不能为空"),
            requireText(request.evidenceReference(), "红线证据引用不能为空"),
            request.sourceVersionId(),
            false,
            null,
            null,
            null,
            null,
            traceId());
        validateRiskMatrixBinding(draftRule);
        repository.findByTenantIdAndRedlineId(tenantId, redlineId).ifPresent(existing -> {
            throw new ApiException(ErrorCode.CONFLICT, "红线 ID 已存在：" + redlineId);
        });
        repository.findByTenantIdAndRedlineKeyAndRedlineVersion(
            tenantId, redlineKey, redlineVersion).ifPresent(existing -> {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "同一红线编码版本已存在：" + redlineKey + "@" + redlineVersion);
            });

        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        ClinicalRedlineRule saved = repository.save(new ClinicalRedlineRule(
            draftRule.id(),
            draftRule.redlineId(),
            draftRule.tenantId(),
            draftRule.category(),
            draftRule.triggerPoint(),
            draftRule.scopeType(),
            draftRule.scopeRef(),
            draftRule.activeScopeKey(),
            draftRule.redlineKey(),
            draftRule.redlineVersion(),
            draftRule.status(),
            draftRule.hazardSeverity(),
            draftRule.riskMatrixId(),
            draftRule.riskMatrixVersion(),
            draftRule.reviewRequirement(),
            draftRule.silentRunHours(),
            draftRule.releaseGate(),
            draftRule.title(),
            draftRule.clinicalHazard(),
            draftRule.conditionDsl(),
            draftRule.evidenceSource(),
            draftRule.evidenceReference(),
            draftRule.sourceVersionId(),
            draftRule.lowerTenantOverrideAllowed(),
            now,
            actor,
            now,
            actor,
            traceId));
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_engine_clinical_redline",
            saved.redlineId(),
            "创建临床安全红线草稿");
        return saved.toResponse();
    }

    @Transactional
    public ClinicalRedlineTrialResponse dryRun(ClinicalRedlineDryRunRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行请求不能为空");
        }
        String tenantId = tenantId();
        ClinicalRedlineRule rule = repository.findByTenantIdAndRedlineId(
                tenantId, requireText(request.redlineId(), "红线 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "红线不存在"));
        if (rule.status() != ClinicalRedlineStatus.DRAFT
                && rule.status() != ClinicalRedlineStatus.SILENT_RUNNING) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿或静默试运行中的红线可以记录试运行证据");
        }
        validateHazardBundle(rule);
        validateRiskMatrixBinding(rule);
        validateTrialWindowAndCounts(request);
        long actualHours = Duration.between(request.observedFrom(), request.observedTo()).toHours();
        boolean gatePassed = actualHours >= rule.silentRunHours() && request.safetyIncidentCount() == 0;
        ClinicalRedlineTrialStatus status = gatePassed
            ? ClinicalRedlineTrialStatus.PASSED
            : ClinicalRedlineTrialStatus.FAILED;
        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        ClinicalRedlineTrial saved = trialRepository.save(new ClinicalRedlineTrial(
            null,
            "crt-" + UUID.randomUUID(),
            tenantId,
            rule.redlineId(),
            rule.redlineKey(),
            rule.redlineVersion(),
            status,
            request.observedFrom(),
            request.observedTo(),
            rule.silentRunHours(),
            actualHours,
            request.evaluatedCaseCount(),
            request.matchedCaseCount(),
            request.falsePositiveCaseCount(),
            request.safetyIncidentCount(),
            gatePassed,
            requireText(request.evidenceReference(), "试运行证据引用不能为空"),
            trimToNull(request.operatorNote()),
            now,
            actor,
            traceId));
        repository.save(rule.withStatus(ClinicalRedlineStatus.SILENT_RUNNING, now, actor, traceId));
        auditRecorder.record(AuditAction.EXECUTE, "mk_engine_clinical_redline_trial",
            saved.trialId(), "记录临床安全红线静默试运行证据");
        return saved.toResponse();
    }

    @Transactional
    public ClinicalRedlineResponse promote(ClinicalRedlinePromoteRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线上线请求不能为空");
        }
        String tenantId = tenantId();
        ClinicalRedlineRule rule = repository.findByTenantIdAndRedlineId(
                tenantId, requireText(request.redlineId(), "红线 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "红线不存在"));
        if (rule.status() != ClinicalRedlineStatus.SILENT_RUNNING) {
            throw new ApiException(ErrorCode.CONFLICT, "只有静默试运行中的红线可以上线");
        }
        if (!rule.redlineVersion().equals(requireText(request.expectedRedlineVersion(), "预期红线版本不能为空"))) {
            throw new ApiException(ErrorCode.CONFLICT, "红线版本与上线请求不一致");
        }
        ClinicalRedlineTrial trial = trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                tenantId,
                rule.redlineId(),
                requireText(request.trialId(), "试运行证据 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "静默试运行证据不存在"));
        if (!trial.redlineVersion().equals(rule.redlineVersion())) {
            throw new ApiException(ErrorCode.CONFLICT, "静默试运行证据与当前红线版本不一致");
        }
        if (trial.status() != ClinicalRedlineTrialStatus.PASSED
                || !trial.gatePassed()
                || trial.actualSilentHours() < rule.silentRunHours()
                || trial.safetyIncidentCount() > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "静默试运行未达标，禁止上线");
        }
        if (rule.lowerTenantOverrideAllowed()) {
            throw new ApiException(ErrorCode.CONFLICT, "安全红线禁止下级关闭，lowerTenantOverrideAllowed 必须为 false");
        }
        VersionPublishQualityGate qualityGate = safetyPublishQualityGate(rule, trial, request.promotionReason());
        if (!qualityGatePassed(qualityGate)) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "临床安全红线发布质量校验未全部通过：" + qualityGate.summary());
        }
        requireText(request.promotionReason(), "红线上线原因不能为空");
        repository.findByTenantIdAndActiveScopeKeyAndStatus(
                tenantId, rule.computedActiveScopeKey(), ClinicalRedlineStatus.ACTIVE)
            .filter(active -> !active.redlineId().equals(rule.redlineId()))
            .ifPresent(active -> {
                throw new ApiException(ErrorCode.CONFLICT, "同适用域已有生效红线，需先走安全撤回或版本切换流程");
            });
        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        ClinicalRedlineRule activated = repository.save(rule.withStatus(
            ClinicalRedlineStatus.ACTIVE, now, actor, traceId));
        AssetVersion assetVersion = registerUnifiedSafetyAsset(activated, trial, request.promotionReason(), actor, traceId);
        publishUnifiedSafetyAsset(assetVersion, request.promotionReason(), actor, traceId, qualityGate);
        auditRecorder.record(AuditAction.PUBLISH, "mk_engine_clinical_redline",
            activated.redlineId(), "临床安全红线静默试运行达标后上线");
        return activated.toResponse();
    }

    private void validateHazardBundle(ClinicalRedlineRule rule) {
        if (rule.hazardSeverity() == null
                || rule.reviewRequirement() == null
                || !hasText(rule.riskMatrixId())
                || !hasText(rule.riskMatrixVersion())
                || !hasText(rule.clinicalHazard())
                || !hasText(rule.conditionDsl())
                || !hasText(rule.evidenceSource())
                || !hasText(rule.evidenceReference())
                || !hasText(rule.releaseGate())) {
            throw new ApiException(ErrorCode.CONFLICT, "红线危害分析、证据来源和风险矩阵绑定不能为空");
        }
    }

    private void requireValidDraftPayload(ClinicalRedlineDraftRequest request) {
        if (request.category() == null
                || request.hazardSeverity() == null
                || request.reviewRequirement() == null
                || !hasText(request.riskMatrixId())
                || !hasText(request.riskMatrixVersion())
                || !hasText(request.clinicalHazard())
                || !hasText(request.conditionDsl())
                || !hasText(request.evidenceSource())
                || !hasText(request.evidenceReference())
                || !hasText(request.releaseGate())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线危害分析、证据来源和风险矩阵绑定不能为空");
        }
        if (request.lowerTenantOverrideAllowed()) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "安全红线禁止下级关闭，lowerTenantOverrideAllowed 必须为 false");
        }
        canonicalConditionDsl(request.conditionDsl());
    }

    private RedlineScope validateScope(String tenantId, String rawScopeType, String rawScopeRef) {
        String scopeType = requireText(rawScopeType, "红线适用域类型不能为空").toUpperCase();
        String scopeRef = requireText(rawScopeRef, "红线适用域引用不能为空");
        OrgScope current = RequestContext.currentOrgScope();
        String expected = switch (scopeType) {
            case "TENANT" -> tenantId;
            case "REGION" -> current == null ? null : current.groupId();
            case "FACILITY" -> current == null ? null : firstNonBlank(current.siteId(), current.hospitalId());
            case "CAMPUS" -> current == null ? null : current.campusId();
            case "DEPARTMENT" -> current == null ? null : current.departmentId();
            case "WARD" -> current == null ? null : current.wardId();
            default -> throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "红线适用域类型不受支持: " + rawScopeType);
        };
        if (!scopeRef.equals(expected)) {
            throw new ApiException(
                ErrorCode.ORG_SCOPE_DENIED,
                "红线适用域必须与当前组织上下文一致");
        }
        return new RedlineScope(scopeType, scopeRef);
    }

    private String canonicalConditionDsl(String conditionDsl) {
        String value = requireText(conditionDsl, "红线条件 DSL 不能为空");
        try {
            JsonNode node = JSON.readTree(value);
            if (!node.isObject()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "红线条件 DSL 必须是 JSON 对象");
            }
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线条件 DSL 不是合法 JSON", exception);
        }
    }

    private AssetVersion registerUnifiedSafetyAsset(
            ClinicalRedlineRule rule,
            ClinicalRedlineTrial trial,
            String promotionReason,
            String actor,
            String traceId) {
        String assetIdentity = safetyAssetIdentity(rule);
        return versionService.registerDraft(new AssetVersionRegisterCommand(
            rule.tenantId(),
            VersionedAssetType.SAFETY,
            assetIdentity,
            null,
            "ALL",
            writeContent(safetyAssetContent(rule, trial, promotionReason)),
            null,
            "clinical-redline:" + rule.redlineId() + ":" + rule.redlineVersion(),
            actor,
            traceId,
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED,
            List.of(new AssetDependencyDeclaration(
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                null,
                null,
                AssetDependencyKind.RUNTIME_ASSET))
        ));
    }

    private void publishUnifiedSafetyAsset(
            AssetVersion assetVersion,
            String promotionReason,
            String actor,
            String traceId,
            VersionPublishQualityGate qualityGate) {
        releasePort.publish(new VersionReleaseCommand(
            assetVersion.tenantId(),
            VersionedAssetType.SAFETY,
            assetVersion.assetIdentity(),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            assetVersion.applicableScope(),
            VersionReleaseScopeType.ALL,
            null,
            RolloutPolicy.all(),
            "临床安全红线静默试运行达标：" + requireText(promotionReason, "红线上线原因不能为空"),
            null,
            actor,
            traceId,
            qualityGate
        ));
    }

    private VersionPublishQualityGate safetyPublishQualityGate(
            ClinicalRedlineRule rule,
            ClinicalRedlineTrial trial,
            String promotionReason) {
        String reason = requireText(promotionReason, "红线上线原因不能为空");
        JsonNode condition = parseConditionDsl(rule.conditionDsl());
        List<String> unknownFields = ContextFieldPathPolicy.unknownFields(
            ContextFieldPathPolicy.ruleDslFields(condition));
        boolean schemaValid = condition.isObject()
            && hasText(rule.redlineId())
            && rule.category() != null
            && hasText(rule.redlineKey())
            && hasText(rule.redlineVersion());
        boolean terminologyBindingComplete = unknownFields.isEmpty();
        boolean riskMatrixBindingVerified = riskMatrixBindingVerified(rule);
        boolean dependencyIntegrityVerified = riskMatrixBindingVerified
            && hasText(rule.evidenceSource())
            && hasText(rule.evidenceReference())
            && hasText(trial.evidenceReference())
            && trial.redlineVersion().equals(rule.redlineVersion());
        boolean safetyMonotonicityVerified = !rule.lowerTenantOverrideAllowed()
            && rule.hazardSeverity() != null
            && rule.reviewRequirement() != null
            && trial.safetyIncidentCount() == 0;
        boolean impactSimulationPassed = trial.status() == ClinicalRedlineTrialStatus.PASSED
            && trial.gatePassed()
            && trial.actualSilentHours() >= rule.silentRunHours()
            && trial.evaluatedCaseCount() > 0
            && trial.matchedCaseCount() >= 0
            && trial.falsePositiveCaseCount() <= trial.matchedCaseCount();
        String summary = safetyQualitySummary(
            rule,
            trial,
            reason,
            unknownFields,
            riskMatrixBindingVerified,
            schemaValid,
            terminologyBindingComplete,
            dependencyIntegrityVerified,
            safetyMonotonicityVerified,
            impactSimulationPassed);
        return new VersionPublishQualityGate(
            schemaValid,
            terminologyBindingComplete,
            dependencyIntegrityVerified,
            safetyMonotonicityVerified,
            impactSimulationPassed,
            summary
        );
    }

    private boolean qualityGatePassed(VersionPublishQualityGate qualityGate) {
        return qualityGate != null
            && qualityGate.schemaValid()
            && qualityGate.terminologyBindingComplete()
            && qualityGate.dependencyIntegrityVerified()
            && qualityGate.safetyMonotonicityVerified()
            && qualityGate.impactSimulationPassed();
    }

    private JsonNode parseConditionDsl(String conditionDsl) {
        try {
            return JSON.readTree(requireText(conditionDsl, "红线条件 DSL 不能为空"));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线条件 DSL 不是合法 JSON", exception);
        }
    }

    private String safetyQualitySummary(
            ClinicalRedlineRule rule,
            ClinicalRedlineTrial trial,
            String promotionReason,
            List<String> unknownFields,
            boolean riskMatrixBindingVerified,
            boolean schemaValid,
            boolean terminologyBindingComplete,
            boolean dependencyIntegrityVerified,
            boolean safetyMonotonicityVerified,
            boolean impactSimulationPassed) {
        List<String> failed = new java.util.ArrayList<>();
        if (!schemaValid) {
            failed.add("结构校验");
        }
        if (!terminologyBindingComplete) {
            failed.add("术语字段绑定:" + String.join(",", unknownFields));
        }
        if (!riskMatrixBindingVerified) {
            failed.add("风险矩阵绑定:" + rule.riskMatrixId() + "@" + rule.riskMatrixVersion());
        }
        if (!dependencyIntegrityVerified) {
            failed.add("依赖完整性");
        }
        if (!safetyMonotonicityVerified) {
            failed.add("安全单调性");
        }
        if (!impactSimulationPassed) {
            failed.add("影响评估");
        }
        if (!failed.isEmpty()) {
            return "未通过：" + String.join("、", failed);
        }
        return "临床安全红线静默试运行达标；结构校验、术语字段绑定、依赖完整性、安全单调性、影响评估均通过。"
            + "观察病例 " + trial.evaluatedCaseCount()
            + " 例，命中 " + trial.matchedCaseCount()
            + " 例，安全事件 " + trial.safetyIncidentCount()
            + " 例；红线 " + rule.redlineKey() + "@" + rule.redlineVersion()
            + "；上线原因：" + promotionReason;
    }

    private Map<String, Object> safetyAssetContent(
            ClinicalRedlineRule rule,
            ClinicalRedlineTrial trial,
            String promotionReason) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", SCHEMA_VERSION);
        value.put("redlineId", rule.redlineId());
        value.put("category", rule.category());
        value.put("triggerPoint", rule.triggerPoint());
        value.put("scopeType", rule.scopeType());
        value.put("scopeRef", rule.scopeRef());
        value.put("redlineKey", rule.redlineKey());
        value.put("redlineVersion", rule.redlineVersion());
        value.put("hazardSeverity", rule.hazardSeverity());
        value.put("riskMatrixId", rule.riskMatrixId());
        value.put("riskMatrixVersion", rule.riskMatrixVersion());
        value.put("reviewRequirement", rule.reviewRequirement());
        value.put("silentRunHours", rule.silentRunHours());
        value.put("releaseGate", rule.releaseGate());
        value.put("title", rule.title());
        value.put("clinicalHazard", rule.clinicalHazard());
        value.put("conditionDsl", rule.conditionDsl());
        value.put("evidenceSource", rule.evidenceSource());
        value.put("evidenceReference", rule.evidenceReference());
        value.put("sourceVersionId", rule.sourceVersionId());
        value.put("lowerTenantOverrideAllowed", rule.lowerTenantOverrideAllowed());
        value.put("trialId", trial.trialId());
        value.put("trialEvidenceReference", trial.evidenceReference());
        value.put("promotionReason", promotionReason.trim());
        return value;
    }

    private String writeContent(Map<String, Object> content) {
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "临床安全红线资产正文序列化失败", exception);
        }
    }

    private String safetyAssetIdentity(ClinicalRedlineRule rule) {
        return "SAFETY." + requireText(rule.redlineKey(), "红线编码不能为空").trim();
    }

    private boolean riskMatrixBindingVerified(ClinicalRedlineRule rule) {
        String tenantId = requireText(rule.tenantId(), "租户不能为空");
        String matrixId = requireText(rule.riskMatrixId(), "风险矩阵 ID 不能为空");
        String matrixVersion = requireText(rule.riskMatrixVersion(), "风险矩阵版本不能为空");
        return riskMatrices
            .findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                tenantId, matrixVersion)
            .stream()
            .anyMatch(matrix -> riskMatrixMatches(matrix, rule));
    }

    private boolean riskMatrixMatches(CdssRiskMatrixRule matrix, ClinicalRedlineRule rule) {
        return matrix != null
            && matrix.status() == CdssRiskMatrixStatus.ACTIVE
            && requireText(rule.riskMatrixId(), "风险矩阵 ID 不能为空").equals(matrix.matrixId())
            && requireText(rule.triggerPoint(), "红线触发点不能为空").equals(matrix.triggerPoint())
            && rule.hazardSeverity() == matrix.severityLevel()
            && rule.reviewRequirement() == matrix.reviewRequirement()
            && rule.silentRunHours() == matrix.silentRunHours()
            && requireText(rule.releaseGate(), "上线门槛不能为空").equals(matrix.releaseGate())
            && !matrix.autoExecutionAllowed();
    }

    private void validateRiskMatrixBinding(ClinicalRedlineRule rule) {
        if (!riskMatrixBindingVerified(rule)) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "临床安全红线风险矩阵绑定未通过验证："
                    + rule.riskMatrixId() + "@" + rule.riskMatrixVersion());
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private record RedlineScope(String scopeType, String scopeRef) {
    }

    private void validateTrialWindowAndCounts(ClinicalRedlineDryRunRequest request) {
        if (request.observedFrom() == null || request.observedTo() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行观察窗口不能为空");
        }
        if (!request.observedTo().isAfter(request.observedFrom())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行观察结束时间必须晚于开始时间");
        }
        if (request.evaluatedCaseCount() < 0
                || request.matchedCaseCount() < 0
                || request.falsePositiveCaseCount() < 0
                || request.safetyIncidentCount() < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行统计计数不能为负数");
        }
        if (request.matchedCaseCount() > request.evaluatedCaseCount()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线命中数不能超过评估病例数");
        }
        if (request.falsePositiveCaseCount() > request.matchedCaseCount()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "误报数不能超过命中数");
        }
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

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
