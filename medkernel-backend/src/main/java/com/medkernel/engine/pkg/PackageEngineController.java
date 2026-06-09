package com.medkernel.engine.pkg;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.medkernel.engine.pathway.PathwayKnowledgePackageService;
import com.medkernel.engine.terminology.TerminologyKnowledgePackageService;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识包发布与同步 REST 控制器。
 *
 * <p>承担知识包创建、资产条目添加、首发平台包引用、差异计算与影响分析、多通道同步及回滚终点。
 * 权限分拆为 {@code package.read} / {@code package.publish} / {@code package.rollback} / {@code tenant.override}。
 */
@RestController
@RequestMapping("/api/v1/engine/pkg/packages")
@DataScope(requireTenant = true)
public class PackageEngineController {

    private final PackageEngineService service;
    private final PackageInheritanceImpactService inheritanceImpactService;
    private final PackageEntitlementService entitlementService;
    private final TerminologyKnowledgePackageService terminologyPackageService;
    private final PathwayKnowledgePackageService pathwayPackageService;

    public PackageEngineController(
            PackageEngineService service,
            PackageInheritanceImpactService inheritanceImpactService,
            PackageEntitlementService entitlementService,
            TerminologyKnowledgePackageService terminologyPackageService,
            PathwayKnowledgePackageService pathwayPackageService) {
        this.service = service;
        this.inheritanceImpactService = inheritanceImpactService;
        this.entitlementService = entitlementService;
        this.terminologyPackageService = terminologyPackageService;
        this.pathwayPackageService = pathwayPackageService;
    }

    /**
     * 创建知识包草稿。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping
    @PreAuthorize("@perm.has('package.publish')")
    public ResponseEntity<ApiResult<PackageResponse>> createPackage(
            @RequestBody @Valid PackageCreateRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createPackage(request)));
    }

    /**
     * 把当前组织范围的已确认术语映射冻结为知识包草稿。
     *
     * <p>权限：{@code term.write}。
     */
    @PostMapping("/terminology")
    @PreAuthorize("@perm.has('term.write')")
    public ResponseEntity<ApiResult<PackageResponse>> buildTerminologyPackage(
            @RequestBody @Valid TerminologyPackageBuildRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(terminologyPackageService.build(request)));
    }

    /**
     * 把专病画像定义保存为统一路径知识包草稿。
     *
     * <p>权限：{@code pathway.write}。
     */
    @PostMapping("/pathway")
    @PreAuthorize("@perm.has('pathway.write')")
    public ResponseEntity<ApiResult<PackageResponse>> buildPathwayPackage(
            @RequestBody @Valid PathwayPackageBuildRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(pathwayPackageService.build(request)));
    }

    /**
     * 分页查询当前租户下的知识包列表。
     *
     * <p>权限：通用查询需要 {@code package.read}；显式查询术语或路径包时可使用对应领域读权限。
     */
    @GetMapping
    @PreAuthorize("""
        @perm.has('package.read')
        || (#assetType == T(com.medkernel.engine.versioning.VersionedAssetType).TERMINOLOGY
            && @perm.has('term.read'))
        || (#assetType == T(com.medkernel.engine.versioning.VersionedAssetType).PATHWAY
            && @perm.has('pathway.read'))
        """)
    public ApiResult<PageResponse<PackageSummaryResponse>> listPackages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) KnowledgePackageStatus status,
            @RequestParam(required = false) VersionedAssetType assetType) {
        return ApiResult.ok(service.listPackages(
            new PageRequest(page, size, sort),
            new PackageListFilter(keyword, status, assetType)
        ));
    }

    /**
     * 查询可用于试点首发的一键配置包模板。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/pilot-templates")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<List<PilotPackageTemplateResponse>> listPilotTemplates() {
        return ApiResult.ok(service.listPilotTemplates());
    }

    /**
     * 应用首发模板推荐的平台包引用，并可登记初始覆盖。
     *
     * <p>权限：{@code tenant.override}。
     */
    @PostMapping("/pilot-templates/{templateCode}/references")
    @PreAuthorize("@perm.has('tenant.override')")
    public ResponseEntity<ApiResult<PilotPackageTemplateApplyResponse>> applyPilotTemplateReferences(
            @PathVariable String templateCode,
            @RequestBody @Valid PilotPackageTemplateApplyRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.applyPilotTemplateReferences(templateCode, request)));
    }

    /**
     * 查询配置资产准备就绪状态，供实施向导和配置包中心共用。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/asset-readiness")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<PackageAssetReadinessResponse> assetReadiness() {
        return ApiResult.ok(service.getAssetReadiness());
    }

    /**
     * 分页查询受限平台包的租户授权。
     *
     * <p>权限：{@code platform.publish}。
     */
    @GetMapping("/{packageId}/entitlements")
    @PreAuthorize("@perm.has('platform.publish')")
    public ApiResult<PageResponse<PackageEntitlementResponse>> listEntitlements(
            @PathVariable String packageId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(entitlementService.list(packageId, new PageRequest(page, size, sort)));
    }

    /**
     * 为目标租户开通或续期受限平台包授权。
     *
     * <p>权限：{@code platform.publish}。
     */
    @PostMapping("/{packageId}/entitlements")
    @PreAuthorize("@perm.has('platform.publish')")
    public ResponseEntity<ApiResult<PackageEntitlementResponse>> grantEntitlement(
            @PathVariable String packageId,
            @RequestBody @Valid PackageEntitlementGrantRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(entitlementService.grant(packageId, request)));
    }

    /**
     * 撤销目标租户的受限平台包授权。
     *
     * <p>权限：{@code platform.publish}。
     */
    @PostMapping("/{packageId}/entitlements/{tenantId}:revoke")
    @PreAuthorize("@perm.has('platform.publish')")
    public ApiResult<PackageEntitlementResponse> revokeEntitlement(
            @PathVariable String packageId,
            @PathVariable String tenantId,
            @RequestBody @Valid PackageEntitlementRevokeRequest request) {
        validateContext(request);
        return ApiResult.ok(entitlementService.revoke(packageId, tenantId, request));
    }

    /**
     * 查询平台上游版本变更对当前租户继承链的影响与 rebase 提示。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/inheritance-impact")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<PackageInheritanceImpactResponse> inheritanceImpact(
            @RequestParam VersionedAssetType assetType,
            @RequestParam String assetIdentity,
            @RequestParam String applicableScope,
            @RequestParam String upstreamVersionId) {
        return ApiResult.ok(inheritanceImpactService.analyze(
            RequestContext.currentOrgScope().tenantId(),
            assetType,
            assetIdentity,
            applicableScope,
            upstreamVersionId
        ));
    }

    /**
     * 获取知识包详情（含所包含的全部子资产条目列表）。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<PackageDetailResponse> packageDetail(@PathVariable String packageId) {
        return ApiResult.ok(service.packageDetail(packageId));
    }

    /**
     * 向知识包草稿中添加一个子项资产条目（如规则、路径等）。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/{packageId}/items")
    @PreAuthorize("@perm.has('package.publish')")
    public ResponseEntity<ApiResult<PackageItemResponse>> addPackageItem(
            @PathVariable String packageId,
            @RequestBody @Valid PackageItemRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.addPackageItem(packageId, request)));
    }

    /**
     * 计算该知识包与指定基准版本包之间的版本差异及影响分析。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}/diff")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<PackageDiffResponse> calculateDiff(
            @PathVariable String packageId,
            @RequestParam(required = false) String basePackageId) {
        return ApiResult.ok(service.calculateDiff(packageId, basePackageId));
    }

    /**
     * 校验包是否满足发布前门禁。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/{packageId}/validate")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<PackageValidateResponse> validatePackage(
            @PathVariable String packageId,
            @RequestBody @Valid PackageOperationRequest request) {
        validateContext(request);
        return ApiResult.ok(service.validatePackage(packageId));
    }

    /**
     * 导出知识包差异与影响范围证据。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}/diff/export")
    @PreAuthorize("@perm.has('package.read')")
    public void exportDiffEvidence(
            @PathVariable String packageId,
            @RequestParam(required = false) String basePackageId,
            HttpServletResponse response) throws IOException {
        String evidence = service.exportDiffEvidence(packageId, basePackageId);
        String safePackageId = packageId.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/x-ndjson;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"package-diff-" + safePackageId + ".jsonl\"");
        try (OutputStream output = response.getOutputStream()) {
            output.write(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 导出可离线安装的完整知识包。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}/offline/export")
    @PreAuthorize("@perm.has('package.read')")
    public void exportOfflinePackage(
            @PathVariable String packageId,
            @RequestParam String targetOrgUnitId,
            HttpServletResponse response) throws IOException {
        String exportedPackage = service.exportOfflinePackage(packageId, targetOrgUnitId);
        String safePackageId = packageId.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"package-offline-" + safePackageId + ".json\"");
        try (OutputStream output = response.getOutputStream()) {
            output.write(exportedPackage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 导出包发布同步证据与异常适配器清单。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}/sync-logs/export")
    @PreAuthorize("@perm.has('package.read')")
    public void exportSyncEvidence(
            @PathVariable String packageId,
            HttpServletResponse response) throws IOException {
        String evidence = service.exportSyncEvidence(packageId);
        String safePackageId = packageId.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/x-ndjson;charset=utf-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"package-sync-evidence-" + safePackageId + ".jsonl\"");
        try (OutputStream output = response.getOutputStream()) {
            output.write(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 导入离线配置包，验签后以本地草案落库。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/offline/import")
    @PreAuthorize("@perm.has('package.publish')")
    public ResponseEntity<ApiResult<PackageOfflineImportResponse>> importOfflinePackage(
            @RequestBody @Valid PackageOfflineImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.importOfflinePackage(request)));
    }

    /**
     * 触发包灰度/全量同步发布。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/{packageId}/sync")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<PackageSyncResponse> syncPackage(
            @PathVariable String packageId,
            @RequestBody @Valid PackageSyncRequest request) {
        validateContext(request);
        return ApiResult.ok(service.syncPackage(packageId, request));
    }

    /**
     * 触发包灰度或全量发布，复用真实同步状态机。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/{packageId}/release")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<PackageSyncResponse> releasePackage(
            @PathVariable String packageId,
            @RequestBody @Valid PackageSyncRequest request) {
        validateContext(request);
        return ApiResult.ok(service.releasePackage(packageId, request));
    }

    /**
     * 查询包发布同步日志。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping("/{packageId}/sync-logs")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<List<SyncLogResponse>> listSyncLogs(@PathVariable String packageId) {
        return ApiResult.ok(service.listSyncLogs(packageId));
    }

    /**
     * 一键快速回滚在用包版本到指定历史点。
     *
     * <p>权限：{@code package.rollback}。
     */
    @PostMapping("/{packageId}/rollback")
    @PreAuthorize("@perm.has('package.rollback')")
    public ApiResult<PackageResponse> rollbackPackage(
            @PathVariable String packageId,
            @RequestBody @Valid PackageRollbackRequest request) {
        validateContext(request);
        return ApiResult.ok(service.rollbackPackage(packageId, request));
    }

    /**
     * 获取当前租户下可用于配置包发布的适配器。
     *
     * <p>权限：{@code package.read}。
     *
     * @return 发布适配器列表
     */
    @GetMapping("/release-adapters")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<List<PackageReleaseAdapterResponse>> listReleaseAdapters() {
        return ApiResult.ok(service.listReleaseAdapters());
    }

    private void validateContext(PackageContextRequest request) {
        request.apiContext().validateTenant(RequestContext.currentOrgScope().tenantId());
    }
}
