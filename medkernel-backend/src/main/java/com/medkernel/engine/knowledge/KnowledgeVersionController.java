package com.medkernel.engine.knowledge;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * MedKernel v1.0 GA · 知识版本状态机 API（GA-ENG-API-03）。
 *
 * <p>提供版本查看 + 激活 + 撤回三组动作。激活/撤回是高风险动作，
 * 需要 {@code knowledge.publish} / {@code knowledge.withdraw} 权限（医务处 / 平台管理员）。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class KnowledgeVersionController {

    private final KnowledgeVersionService versionService;

    public KnowledgeVersionController(KnowledgeVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/identities/{identityId}/versions")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<KnowledgeAssetVersion>> listByIdentity(@PathVariable Long identityId) {
        return ApiResult.ok(versionService.listByIdentity(identityId));
    }

    @PostMapping("/identities/{identityId}/versions")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<KnowledgeCandidateResponse> createVersion(@PathVariable Long identityId,
                                                               @Valid @RequestBody KnowledgeVersionCreateRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(versionService.classifyCandidate(identityId, request));
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeAssetVersion> get(@PathVariable Long versionId) {
        return ApiResult.ok(versionService.getVersion(versionId));
    }

    @GetMapping("/review-queue")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeReviewQueueItem>> reviewQueue(
            @RequestParam(defaultValue = "30") int withinDays,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(versionService.listReviewQueue(withinDays, new PageRequest(page, size, sort)));
    }

    @PostMapping("/identities/{identityId}/versions/{versionId}/submit")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeAssetVersion> submit(@PathVariable Long identityId,
                                                   @PathVariable Long versionId,
                                                   @Valid @RequestBody KnowledgeActionRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(versionService.submit(identityId, versionId, request));
    }

    @GetMapping("/identities/{identityId}/versions/{versionId}/replay")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeReplayResponse> replay(@PathVariable Long identityId,
                                                     @PathVariable Long versionId,
                                                     @RequestParam(required = false) String packageVersion,
                                                     @RequestParam(required = false) String snapshotId) {
        return ApiResult.ok(versionService.replayVersion(identityId, versionId, packageVersion, snapshotId));
    }

    @GetMapping("/identities/{identityId}/candidates")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeCandidateResponse> candidates(@PathVariable Long identityId) {
        return ApiResult.ok(versionService.listCandidates(identityId));
    }

    @PostMapping("/candidates/{candidateId}/review")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeCandidateResponse> reviewCandidate(@PathVariable Long candidateId,
                                                                 @Valid @RequestBody KnowledgeCandidateReviewRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(versionService.reviewCandidate(candidateId, request));
    }

    @GetMapping("/candidates/{candidateId}/diff")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeCandidateResponse> diffCandidate(@PathVariable Long candidateId) {
        return ApiResult.ok(versionService.diffCandidate(candidateId));
    }

    /**
     * 审核激活：将版本推到 ACTIVE，旧版降为 SUPERSEDED。
     *
     * <p>高风险版本（{@code riskLevel = HIGH}）必须填写 reason；
     * 详细规范 §1797-1806 要求 "高风险逐条确认 + 留证"。
     */
    @PostMapping("/identities/{identityId}/versions/{versionId}/activate")
    @PreAuthorize("@perm.has('knowledge.publish')")
    public ApiResult<KnowledgeAssetVersion> activate(@PathVariable Long identityId,
                                                     @PathVariable Long versionId,
                                                     @Valid @RequestBody(required = false) ActivateVersionRequest req) {
        String reason = req == null ? null : req.reason();
        return ApiResult.ok(versionService.activate(identityId, versionId, reason));
    }

    /**
     * 紧急撤回：ACTIVE → WITHDRAWN。reason 必填。
     */
    @PostMapping("/identities/{identityId}/versions/{versionId}/withdraw")
    @PreAuthorize("@perm.has('knowledge.withdraw')")
    public ApiResult<KnowledgeAssetVersion> withdraw(@PathVariable Long identityId,
                                                     @PathVariable Long versionId,
                                                     @Valid @RequestBody WithdrawVersionRequest req) {
        return ApiResult.ok(versionService.withdraw(identityId, versionId, req.reason()));
    }

    /** 版本激活请求体。reason 是激活说明，高风险必填，常风险可空。 */
    public record ActivateVersionRequest(@Size(max = 500) String reason) {}

    /** 版本撤回请求体。reason 永远必填。 */
    public record WithdrawVersionRequest(@Size(min = 1, max = 500) String reason) {}
}
