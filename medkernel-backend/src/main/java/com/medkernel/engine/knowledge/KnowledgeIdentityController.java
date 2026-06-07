package com.medkernel.engine.knowledge;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * MedKernel v1.0 GA · 知识身份与来源 API（GA-ENG-API-03）。
 *
 * <p>对应详细规范 §1797-1806 / §S37 "床旁知识查阅"。
 * 临床医生通过本接口拉取当前权威版本；知识治理人员登记来源、创建资产并查看引用链。
 *
 * <p>访问控制：
 * <ul>
 *   <li>类级 {@link DataScope}({@code requireTenant=true})：所有方法都需要租户上下文</li>
 *   <li>方法级 {@code @PreAuthorize}：按读取、写入和发布动作分别校验权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class KnowledgeIdentityController {

    private final KnowledgeIdentityService service;

    public KnowledgeIdentityController(KnowledgeIdentityService service) {
        this.service = service;
    }

    @GetMapping("/identities")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeIdentity>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) KnowledgeDomain domain,
            @RequestParam(required = false) String specialtyId,
            @RequestParam(required = false) KnowledgeIdentityStatus status,
            @RequestParam(required = false) String keyword) {
        PageRequest req = new PageRequest(page, size, sort);
        KnowledgeIdentityFilter filter = new KnowledgeIdentityFilter(domain, specialtyId, status, keyword);
        return ApiResult.ok(service.page(req, filter));
    }

    @PostMapping("/identities")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<KnowledgeIdentity> create(@Valid @RequestBody KnowledgeIdentityCreateRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.createIdentity(request));
    }

    @GetMapping("/identities/{id}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeIdentity> get(@PathVariable Long id) {
        return ApiResult.ok(service.get(id));
    }

    @GetMapping("/identities/by-code/{identityCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeIdentity> getByCode(@PathVariable String identityCode) {
        return ApiResult.ok(service.getByCode(identityCode));
    }

    @GetMapping("/identities/{id}/active")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeAssetVersion> getActiveVersion(@PathVariable Long id) {
        return ApiResult.ok(service.getActiveVersion(id));
    }

    @GetMapping("/identities/{id}/lineage")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeLineage> getLineage(@PathVariable Long id) {
        return ApiResult.ok(service.getLineage(id));
    }

    /**
     * 获取知识身份的完整来源追溯视图。
     *
     * @param id 知识身份 ID
     * @return 来源、版本和替代历史
     */
    @GetMapping("/identities/{id}/provenance")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeProvenanceResponse> getProvenance(@PathVariable Long id) {
        return ApiResult.ok(service.getProvenance(id));
    }

    @GetMapping("/identities/{id}/citations")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<java.util.List<Citation>> getCitations(@PathVariable Long id) {
        return ApiResult.ok(service.listCitations(id));
    }

    @GetMapping("/identities/{id}/source-evidence")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<java.util.List<KnowledgeSourceEvidence>> getSourceEvidence(@PathVariable Long id) {
        return ApiResult.ok(service.listSourceEvidence(id));
    }

    /**
     * 注册或返回已存在的来源文献。
     *
     * @param request 来源文献注册请求
     * @return 来源文献实体
     */
    @PostMapping("/sources")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<SourceDocument> registerSource(@Valid @RequestBody KnowledgeSourceCreateRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.registerSource(request));
    }

    /**
     * 注册来源文献版本。
     *
     * @param request 来源版本注册请求
     * @return 来源版本实体
     */
    @PostMapping("/sources/{sourceDocumentId}/versions")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<SourceVersion> registerSourceVersion(@PathVariable Long sourceDocumentId,
                                                          @Valid @RequestBody KnowledgeSourceVersionCreateRequest request) {
        request.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.registerSourceVersion(sourceDocumentId, request));
    }

    /**
     * 注册文献片段，计算片段内 textExcerpt 的 SHA-256 摘要哈希，作为引用锚点摘要保护。
     *
     * @param request 片段创建请求
     * @return 文献片段实体
     */
    @PostMapping("/sources/fragments")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<SourceFragment> createFragment(@Valid @RequestBody FragmentCreateRequest request) {
        return ApiResult.ok(service.createFragment(request));
    }

    /**
     * 将知识资产版本绑定到真实来源片段，供发布门禁和床旁溯源共同使用。
     */
    @PostMapping("/citations")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<Citation> createCitation(@Valid @RequestBody CitationCreateRequest request) {
        return ApiResult.ok(service.createCitation(request));
    }
}
