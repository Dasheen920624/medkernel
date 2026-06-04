package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
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
    private final AuditEventPublisher audit;
    private final KnowledgeVersionService knowledgeVersions;

    public DiagnosisKnowledgeService(DiagnosisCriterionRepository criteria,
            DiagnosisDifferentialRepository differentials, DiagnosisCarePointerRepository carePointers,
            DiagnosisTestCaseRepository testCases, DiagnosisConfidencePolicyRepository policies,
            DiagnosisMatcher matcher, AuditEventPublisher audit,
            KnowledgeVersionService knowledgeVersions) {
        this.criteria = criteria;
        this.differentials = differentials;
        this.carePointers = carePointers;
        this.testCases = testCases;
        this.policies = policies;
        this.matcher = matcher;
        this.audit = audit;
        this.knowledgeVersions = knowledgeVersions;
    }

    // —— 诊断标准 ——

    @Transactional
    public DiagnosisCriterion addCriterion(Long versionId, DiagnosisCriterionRequest req) {
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisCriterion saved = criteria.save(new DiagnosisCriterion(null, tenant, versionId,
            req.findingTermCode(), req.direction(), req.weight(), req.valueConstraint(),
            req.temporalConstraint(), req.citationId(), now, actor, now, actor, traceId()));
        audit.publish(AuditAction.CREATE, "mk_diagnosis_criterion", String.valueOf(saved.id()),
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
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisDifferential saved = differentials.save(new DiagnosisDifferential(null, tenant, versionId,
            req.differentialIdentityId(), req.keyPoint(), req.suggestedWorkup(), req.bidirectional(),
            now, actor, now, actor, traceId()));
        audit.publish(AuditAction.CREATE, "mk_diagnosis_differential", String.valueOf(saved.id()),
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
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisCarePointer saved = carePointers.save(new DiagnosisCarePointer(null, tenant, versionId,
            req.pointerType(), req.targetRef(), req.isSoft(), req.description(),
            now, actor, now, actor, traceId()));
        audit.publish(AuditAction.CREATE, "mk_diagnosis_care_pointer", String.valueOf(saved.id()),
            "新增诊疗指针 " + req.pointerType() + " -> " + req.targetRef());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisCarePointer> listCarePointers(Long versionId) {
        return carePointers.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    // —— 测试病例 ——

    @Transactional
    public DiagnosisTestCase addTestCase(Long versionId, DiagnosisTestCaseRequest req) {
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisTestCase saved = testCases.save(new DiagnosisTestCase(null, tenant, versionId,
            req.caseCode(), req.findings(), req.expectedIdentityId(), req.expectedConfidence(),
            now, actor, now, actor, traceId()));
        audit.publish(AuditAction.CREATE, "mk_diagnosis_test_case", String.valueOf(saved.id()),
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
        List<DiagnosisCriterion> versionCriteria = criteria.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        DiagnosisConfidencePolicy policy = resolvePolicy(tenant);
        List<DiagnosisTestCase> cases = testCases.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        for (DiagnosisTestCase tc : cases) {
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
            .or(() -> policies.findByTenantIdAndScopeKey("t-1", "DEFAULT"))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
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
