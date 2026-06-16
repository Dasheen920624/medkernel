package com.medkernel.engine.knowledge.parsing;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.knowledge.material.DocumentMaterialResponse;
import com.medkernel.engine.knowledge.material.DocumentMaterialService;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

/**
 * 文档解析 API（AIK-STD-02）。解析 = 把受控来源文档解析为带锚点的来源片段（走 {@code knowledge.write}，
 * 等同新增受控来源内容）；解析 job 台账供查询（走 {@code knowledge.read}）。
 * 类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class DocumentParseController {

    private final DocumentParseOrchestrationService service;
    private final DocumentMaterialService materials;

    public DocumentParseController(DocumentParseOrchestrationService service, DocumentMaterialService materials) {
        this.service = service;
        this.materials = materials;
    }

    /** 提交一次文档解析（解析为带锚点片段并物化进受控来源）。 */
    @PostMapping("/documents:parse")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocParseJob> parse(@Valid @RequestBody DocumentParseRequest request) {
        return ApiResult.ok(service.submit(request));
    }

    /** 从受管资料库取回原件并创建一次新的重解析 job。 */
    @PostMapping("/documents/parse-jobs/{jobCode}:reparse")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocParseJob> reparse(@PathVariable String jobCode) {
        return ApiResult.ok(service.reparse(jobCode));
    }

    /** 查询单个解析 job 状态。 */
    @GetMapping("/documents/parse-jobs/{jobCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DocParseJob> getJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.getJob(jobCode));
    }

    /** 分页查询解析 job 台账。 */
    @GetMapping("/documents/parse-jobs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<DocParseJob>> listJobs(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listJobs(page, size));
    }

    /** 读取已入库文档原件，返回 Base64 内容并记录审计。 */
    @GetMapping("/materials/{materialId}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DocumentMaterialResponse> getMaterial(@PathVariable Long materialId) {
        return ApiResult.ok(materials.getMaterial(materialId));
    }
}
