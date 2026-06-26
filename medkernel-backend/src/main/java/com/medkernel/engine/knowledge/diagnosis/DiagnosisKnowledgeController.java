package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.shared.context.RequestContext;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 诊断知识维护 API（归 knowledge 客户面，复用知识读写权限）。
 *
 * <p>标准 / 鉴别 / 指针 / 验证病例 add 走 {@code knowledge.write}、list 走 {@code knowledge.read}；
 * 发布激活版本走 {@code knowledge.publish}（与 KnowledgeVersionController.activate 一致，HIGH 风险），
 * 先过验证病例门禁（publishGate）全绿才激活——门禁真正生效。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/diagnosis")
@DataScope(requireTenant = true)
public class DiagnosisKnowledgeController {

    private final DiagnosisKnowledgeService service;

    public DiagnosisKnowledgeController(DiagnosisKnowledgeService service) {
        this.service = service;
    }

    @PostMapping("/assets")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisAssetDraftResponse> createAsset(
            @RequestBody @Valid DiagnosisAssetCreateRequest req) {
        req.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.createAsset(req));
    }

    @PostMapping("/identities/{identityId}/versions")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisAssetDraftResponse> createVersion(
            @PathVariable Long identityId,
            @RequestBody @Valid DiagnosisVersionCreateRequest req) {
        req.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.createVersion(identityId, req));
    }

    // —— 诊断标准 ——

    @PostMapping("/versions/{versionId}/criteria")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisCriterion> addCriterion(@PathVariable Long versionId,
            @RequestBody @Valid DiagnosisCriterionRequest req) {
        return ApiResult.ok(service.addCriterion(versionId, req));
    }

    @GetMapping("/versions/{versionId}/criteria")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiagnosisCriterion>> listCriteria(@PathVariable Long versionId) {
        return ApiResult.ok(service.listCriteria(versionId));
    }

    // —— 鉴别清单 ——

    @PostMapping("/versions/{versionId}/differentials")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisDifferential> addDifferential(@PathVariable Long versionId,
            @RequestBody @Valid DiagnosisDifferentialRequest req) {
        return ApiResult.ok(service.addDifferential(versionId, req));
    }

    @GetMapping("/versions/{versionId}/differentials")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiagnosisDifferential>> listDifferentials(@PathVariable Long versionId) {
        return ApiResult.ok(service.listDifferentials(versionId));
    }

    // —— 诊疗指针 ——

    @PostMapping("/versions/{versionId}/care-pointers")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisCarePointer> addCarePointer(@PathVariable Long versionId,
            @RequestBody @Valid DiagnosisCarePointerRequest req) {
        return ApiResult.ok(service.addCarePointer(versionId, req));
    }

    @GetMapping("/versions/{versionId}/care-pointers")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiagnosisCarePointer>> listCarePointers(@PathVariable Long versionId) {
        return ApiResult.ok(service.listCarePointers(versionId));
    }

    // —— 验证病例 ——

    @PostMapping("/versions/{versionId}/test-cases")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisTestCase> addTestCase(@PathVariable Long versionId,
            @RequestBody @Valid DiagnosisTestCaseRequest req) {
        return ApiResult.ok(service.addTestCase(versionId, req));
    }

    @GetMapping("/versions/{versionId}/test-cases")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiagnosisTestCase>> listTestCases(@PathVariable Long versionId) {
        return ApiResult.ok(service.listTestCases(versionId));
    }

    // —— 发布门禁（必过验证病例门禁才激活；激活=HIGH 风险，走 knowledge.publish）——

    @PostMapping("/identities/{identityId}/versions/{versionId}/publish")
    @PreAuthorize("@perm.has('knowledge.publish')")
    public ApiResult<KnowledgeAssetVersion> publish(@PathVariable Long identityId,
            @PathVariable Long versionId,
            @RequestBody(required = false) @Valid DiagnosisPublishRequest request) {
        String reason = request == null ? null : request.reason();
        Long qualityGateRecordId = request == null ? null : request.qualityGateRecordId();
        return ApiResult.ok(service.publishDiagnosis(identityId, versionId, reason, qualityGateRecordId));
    }

    public record DiagnosisPublishRequest(
        String reason,
        Long qualityGateRecordId
    ) {}
}
