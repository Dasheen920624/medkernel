package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/** 生产知识初始化发行 API；所有内容先进入既有候选审核链，不直接发布医学知识。 */
@RestController
@RequestMapping("/api/v1/engine/knowledge-production/initialization")
@DataScope(requireTenant = true)
public class KnowledgeInitializationController {

    private final KnowledgeInitializationCatalog catalog;
    private final KnowledgeInitializationService initializationService;

    public KnowledgeInitializationController(
            KnowledgeInitializationCatalog catalog,
            KnowledgeInitializationService initializationService) {
        this.catalog = catalog;
        this.initializationService = initializationService;
    }

    /** 读取 KNOWGEN-01～35 稳定生产顺序。 */
    @GetMapping("/catalog")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<KnowledgeInitializationCatalogItem>> catalog() {
        return ApiResult.ok(catalog.listAll());
    }

    /** 服务端解析候选、来源和摘要，形成创建前不可篡改预览。 */
    @PostMapping("/batches/preview")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeInitializationBatchPreview> preview(
            @Valid @RequestBody KnowledgeInitializationBatchDraftRequest request) {
        return ApiResult.ok(initializationService.preview(request));
    }

    /** 按预览摘要创建固定候选集合的初始化发行批次。 */
    @PostMapping("/batches")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeInitializationBatchView> create(
            @Valid @RequestBody KnowledgeInitializationBatchCreateRequest request) {
        return ApiResult.ok(initializationService.create(request));
    }

    /** 读取当前租户初始化发行批次。 */
    @GetMapping("/batches")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<KnowledgeInitializationBatch>> list() {
        return ApiResult.ok(initializationService.list());
    }

    /** 读取批次和服务端固定的候选条目。 */
    @GetMapping("/batches/{batchCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeInitializationBatchView> get(@PathVariable String batchCode) {
        return ApiResult.ok(initializationService.get(batchCode));
    }

    /** 只批量批准批次内待审 LOW 候选；MEDIUM/HIGH 继续由医疗引擎运营人员逐条确认。 */
    @PostMapping("/batches/{batchCode}/approve-low")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeInitializationBatchView> approveLow(
            @PathVariable String batchCode,
            @Valid @RequestBody KnowledgeInitializationBatchApproveRequest request) {
        return ApiResult.ok(initializationService.approveLow(batchCode, request));
    }

    /** 从既有候选审核结果和来源指纹刷新批次状态。 */
    @PostMapping("/batches/{batchCode}/refresh")
    @PreAuthorize("@perm.has('knowledge.review')")
    public ApiResult<KnowledgeInitializationBatchView> refresh(@PathVariable String batchCode) {
        return ApiResult.ok(initializationService.refresh(batchCode));
    }
}
