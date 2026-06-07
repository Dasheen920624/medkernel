package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationCreateRequest;
import com.medkernel.engine.knowledge.CitationRelation;
import com.medkernel.engine.knowledge.FragmentCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeApiContext;
import com.medkernel.engine.knowledge.KnowledgeCandidateResponse;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.knowledge.KnowledgeSourceCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeSourceVersionCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeVersionCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 诊断知识维护服务：标准 / 鉴别 / 指针 / 测试病例 CRUD + 以测试病例全绿为发布门禁。
 *
 * <p>发布走 {@link #publishDiagnosis}：先 {@link #publishGate} 复算全部测试病例，分级与期望一致才调通用版本激活；
 * 不一致抛 {@code ENG_DX_006}，门禁真正生效（非死方法）。置信策略可按租户/科室覆盖，未覆盖回退平台主租户 t-1 DEFAULT。
 */
@Service
public class DiagnosisKnowledgeService {

    private final DiagnosisCriterionRepository criteria;
    private final DiagnosisDifferentialRepository differentials;
    private final DiagnosisCarePointerRepository carePointers;
    private final DiagnosisTestCaseRepository testCases;
    private final DiagnosisConfidencePolicyRepository policies;
    private final DiagnosisMatcher matcher;
    private final AuditRecorder audit;
    private final KnowledgeIdentityService knowledgeIdentities;
    private final KnowledgeVersionService knowledgeVersions;
    private final DiagnosisReferenceValidator references;

    public DiagnosisKnowledgeService(DiagnosisCriterionRepository criteria,
            DiagnosisDifferentialRepository differentials, DiagnosisCarePointerRepository carePointers,
            DiagnosisTestCaseRepository testCases, DiagnosisConfidencePolicyRepository policies,
            DiagnosisMatcher matcher, AuditRecorder audit,
            KnowledgeIdentityService knowledgeIdentities,
            KnowledgeVersionService knowledgeVersions,
            DiagnosisReferenceValidator references) {
        this.criteria = criteria;
        this.differentials = differentials;
        this.carePointers = carePointers;
        this.testCases = testCases;
        this.policies = policies;
        this.matcher = matcher;
        this.audit = audit;
        this.knowledgeIdentities = knowledgeIdentities;
        this.knowledgeVersions = knowledgeVersions;
        this.references = references;
    }

    /**
     * 单事务创建来源、诊断身份、候选版本、来源片段和引用，避免留下不可发布的半成品。
     */
    @Transactional
    public DiagnosisAssetDraftResponse createAsset(DiagnosisAssetCreateRequest request) {
        var context = request.context();
        context.validateTenant(tenant());
        var identityInput = request.identity();
        KnowledgeIdentity identity = knowledgeIdentities.createIdentity(new KnowledgeIdentityCreateRequest(
            context.requestId(), context.traceId(), context.tenantId(), context.groupId(),
            context.hospitalId(), context.campusId(), context.siteId(), context.departmentId(),
            context.specialtyId(), context.userId(), context.roleCodes(), context.packageVersion(),
            identityInput.identityCode(), KnowledgeDomain.DIAGNOSIS, identityInput.subject(),
            identityInput.assetSpecialtyId(), identityInput.description()));
        DiagnosisAssetDraftResponse response = createEvidenceCompleteVersion(
            identity, context, request.source(), request.version(), request.evidence());

        audit.record(AuditAction.CREATE, "knowledge_identity", String.valueOf(identity.id()),
            "创建证据完整的诊断知识草稿 " + identity.identityCode());
        return response;
    }

    /**
     * 在既有诊断身份下创建候选版本，并原子绑定来源版本、片段和引用。
     */
    @Transactional
    public DiagnosisAssetDraftResponse createVersion(Long identityId, DiagnosisVersionCreateRequest request) {
        KnowledgeApiContext context = request.context();
        context.validateTenant(tenant());
        KnowledgeIdentity identity = knowledgeIdentities.get(identityId);
        if (identity.domain() != KnowledgeDomain.DIAGNOSIS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "目标身份不是诊断知识资产");
        }
        DiagnosisAssetDraftResponse response = createEvidenceCompleteVersion(
            identity, context, request.source(), request.version(), request.evidence());
        audit.record(AuditAction.CREATE, "knowledge_asset_version", String.valueOf(response.version().id()),
            "创建诊断知识候选版本 " + identity.identityCode() + ":" + response.version().versionNo());
        return response;
    }

    private DiagnosisAssetDraftResponse createEvidenceCompleteVersion(
            KnowledgeIdentity identity,
            KnowledgeApiContext context,
            DiagnosisAssetCreateRequest.SourceInput sourceInput,
            DiagnosisAssetCreateRequest.VersionInput versionInput,
            DiagnosisAssetCreateRequest.EvidenceInput evidenceInput) {
        validateEvidenceExcerpt(sourceInput.content(), evidenceInput.textExcerpt());
        SourceDocument source = knowledgeIdentities.registerSource(new KnowledgeSourceCreateRequest(
            context.requestId(), context.traceId(), context.tenantId(), context.groupId(),
            context.hospitalId(), context.campusId(), context.siteId(), context.departmentId(),
            context.specialtyId(), context.userId(), context.roleCodes(), context.packageVersion(),
            sourceInput.sourceCode(), sourceInput.sourceType(), sourceInput.authorityLevel(),
            sourceInput.authorityBasis(), sourceInput.title(), sourceInput.publisher(),
            sourceInput.license(), sourceInput.language()));
        SourceVersion sourceVersion = knowledgeIdentities.registerSourceVersion(
            source.id(), new KnowledgeSourceVersionCreateRequest(
                context.requestId(), context.traceId(), context.tenantId(), context.groupId(),
                context.hospitalId(), context.campusId(), context.siteId(), context.departmentId(),
                context.specialtyId(), context.userId(), context.roleCodes(), context.packageVersion(),
                sourceInput.versionNo(), sourceInput.publishedAt(), null, sourceInput.fileUri(),
                sourceInput.language(), sourceInput.content()));
        KnowledgeCandidateResponse candidate = knowledgeVersions.classifyCandidate(
            identity.id(), new KnowledgeVersionCreateRequest(
                context.requestId(), context.traceId(), context.tenantId(), context.groupId(),
                context.hospitalId(), context.campusId(), context.siteId(), context.departmentId(),
                context.specialtyId(), context.userId(), context.roleCodes(), context.packageVersion(),
                versionInput.versionNo(), versionInput.versionLabel(), source.id(), sourceVersion.id(),
                sourceInput.content(), evidenceInput.anchorPath(), versionInput.riskLevel(),
                versionInput.gradeQuality(), versionInput.gradeStrength()));
        KnowledgeAssetVersion version = candidate.candidates().stream().findFirst()
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT, "诊断知识内容与既有版本重复，未创建新的可编辑版本"));
        SourceFragment fragment = knowledgeIdentities.createFragment(new FragmentCreateRequest(
            sourceVersion.id(), evidenceInput.anchorPath(), evidenceInput.anchorLabel(),
            evidenceInput.textExcerpt()));
        Citation citation = knowledgeIdentities.createCitation(new CitationCreateRequest(
            version.id(), fragment.id(), CitationRelation.DERIVED_FROM, 100, null, null));
        return new DiagnosisAssetDraftResponse(identity, version, source, sourceVersion, fragment, citation);
    }

    // —— 诊断标准 ——

    @Transactional
    public DiagnosisCriterion addCriterion(Long versionId, DiagnosisCriterionRequest req) {
        requireEditableDiagnosisVersion(versionId);
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisCriterion saved = criteria.save(new DiagnosisCriterion(null, tenant, versionId,
            req.findingTermCode(), req.direction(), req.weight(), req.valueConstraint(),
            req.temporalConstraint(), req.citationId(), now, actor, now, actor, traceId()));
        audit.record(AuditAction.CREATE, "mk_diagnosis_criterion", String.valueOf(saved.id()),
            "新增诊断标准 " + req.findingTermCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisCriterion> listCriteria(Long versionId) {
        return criteria.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    // —— 鉴别清单 ——

    @Transactional
    public DiagnosisDifferential addDifferential(Long versionId, DiagnosisDifferentialRequest req) {
        KnowledgeAssetVersion version = requireEditableDiagnosisVersion(versionId);
        references.validateDifferential(version.identityId(), req.differentialIdentityId());
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisDifferential saved = differentials.save(new DiagnosisDifferential(null, tenant, versionId,
            req.differentialIdentityId(), req.keyPoint(), req.suggestedWorkup(),
            now, actor, now, actor, traceId()));
        audit.record(AuditAction.CREATE, "mk_diagnosis_differential", String.valueOf(saved.id()),
            "新增鉴别清单 -> 身份 " + req.differentialIdentityId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisDifferential> listDifferentials(Long versionId) {
        return differentials.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    // —— 诊疗指针 ——

    @Transactional
    public DiagnosisCarePointer addCarePointer(Long versionId, DiagnosisCarePointerRequest req) {
        requireEditableDiagnosisVersion(versionId);
        validateCareTarget(req.pointerType(), req.targetType());
        references.validateCareTarget(req.targetType(), req.targetRef());
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisCarePointer saved = carePointers.save(new DiagnosisCarePointer(null, tenant, versionId,
            req.pointerType(), req.targetType(), req.targetRef(), true, req.description(),
            now, actor, now, actor, traceId()));
        audit.record(AuditAction.CREATE, "mk_diagnosis_care_pointer", String.valueOf(saved.id()),
            "新增诊疗指针 " + req.pointerType() + " -> " + req.targetType() + ":" + req.targetRef());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisCarePointer> listCarePointers(Long versionId) {
        return carePointers.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    private void validateCareTarget(DiagnosisCarePointerType pointerType, DiagnosisCareTargetType targetType) {
        boolean compatible = switch (pointerType) {
            case PATHWAY -> targetType == DiagnosisCareTargetType.PATHWAY;
            case TREATMENT, WORKUP ->
                targetType == DiagnosisCareTargetType.RULE || targetType == DiagnosisCareTargetType.KNOWLEDGE;
        };
        if (!compatible) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "诊疗指针类型 " + pointerType + " 不能指向 " + targetType);
        }
    }

    // —— 测试病例 ——

    @Transactional
    public DiagnosisTestCase addTestCase(Long versionId, DiagnosisTestCaseRequest req) {
        requireEditableDiagnosisVersion(versionId);
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisTestCase saved = testCases.save(new DiagnosisTestCase(null, tenant, versionId,
            req.caseCode(), req.findings(), req.expectedIdentityId(), req.expectedConfidence(),
            now, actor, now, actor, traceId()));
        audit.record(AuditAction.CREATE, "mk_diagnosis_test_case", String.valueOf(saved.id()),
            "新增诊断测试病例 " + req.caseCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisTestCase> listTestCases(Long versionId) {
        return testCases.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    // —— 发布门禁 ——

    /** 发布门禁：该版本所有测试病例经命中核心复算，分级与期望一致才放行，否则 ENG_DX_006。 */
    @Transactional(readOnly = true)
    public void publishGate(Long versionId) {
        String tenant = tenant();
        KnowledgeAssetVersion version = knowledgeVersions.getVersion(versionId);
        List<DiagnosisCriterion> versionCriteria = criteria.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        DiagnosisConfidencePolicy policy = resolvePolicy(tenant);
        List<DiagnosisTestCase> cases = testCases.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        if (cases.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_DX_006, "诊断知识至少需要一个回归病例才可发布");
        }
        for (DiagnosisTestCase tc : cases) {
            if (!version.identityId().equals(tc.expectedIdentityId())) {
                throw new ApiException(ErrorCode.ENG_DX_006,
                    "测试病例 " + tc.caseCode() + " 的期望诊断身份不属于当前版本");
            }
            Set<String> findings = parseFindings(tc.findings());
            DiagnosisMatchResult result = matcher.match(findings, versionCriteria, policy);
            if (result.confidence() != tc.expectedConfidence()) {
                throw new ApiException(ErrorCode.ENG_DX_006,
                    "测试病例 " + tc.caseCode() + " 期望 " + tc.expectedConfidence()
                        + " 实得 " + result.confidence());
            }
        }
    }

    /** 发布诊断知识版本：先过测试病例门禁（publishGate）全绿，才调通用版本激活。门禁失败抛 ENG_DX_006。 */
    @Transactional
    public KnowledgeAssetVersion publishDiagnosis(Long identityId, Long versionId, String reason) {
        publishGate(versionId);
        return knowledgeVersions.activate(identityId, versionId, reason);
    }

    /** 当前租户 DEFAULT 优先，未覆盖回退平台主源 t-1（V75 已种子）；都缺才诚实失败 ENG_DX_005。 */
    private DiagnosisConfidencePolicy resolvePolicy(String tenant) {
        return policies.findByTenantIdAndScopeKey(tenant, "DEFAULT")
            .or(() -> policies.findByTenantIdAndScopeKey(PlatformTenant.ID, "DEFAULT"))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
    }

    private KnowledgeAssetVersion requireEditableDiagnosisVersion(Long versionId) {
        KnowledgeAssetVersion version = knowledgeVersions.getVersion(versionId);
        KnowledgeIdentity identity = knowledgeIdentities.get(version.identityId());
        if (identity.domain() != KnowledgeDomain.DIAGNOSIS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "目标版本不是诊断知识资产");
        }
        if (version.status() != KnowledgeVersionStatus.DRAFT
                && version.status() != KnowledgeVersionStatus.CANDIDATE
                && version.status() != KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW
                && version.status() != KnowledgeVersionStatus.UNDER_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT,
                "诊断知识版本当前状态 " + version.status() + " 不允许继续修改");
        }
        return version;
    }

    private void validateEvidenceExcerpt(String sourceContent, String excerpt) {
        if (!sourceContent.contains(excerpt.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "诊断依据原文片段必须来自当前来源原文");
        }
    }

    private Set<String> parseFindings(String raw) {
        // findings 存为逗号分隔标准编码；空安全。
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Set.of(raw.split("\\s*,\\s*"));
    }

    private String tenant() {
        String t = RequestContext.currentOrgScope().tenantId();
        if (t == null || t.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return t;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        return RequestContext.currentTraceId();
    }
}
