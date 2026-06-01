package com.medkernel.engine.pkg;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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
 * <p>承担知识包创建、资产条目添加、差异计算与影响分析、多通道物理同步及回滚终点。
 * 权限分拆为 {@code package.read} / {@code package.publish} / {@code package.rollback}。
 */
@RestController
@RequestMapping("/api/v1/engine/packages")
@DataScope(requireTenant = true)
public class PackageEngineController {

    private final PackageEngineService service;

    public PackageEngineController(PackageEngineService service) {
        this.service = service;
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
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createPackage(request)));
    }

    /**
     * 分页查询当前租户下的知识包列表。
     *
     * <p>权限：{@code package.read}。
     */
    @GetMapping
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<PageResponse<KnowledgePackage>> listPackages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listPackages(new PageRequest(page, size, sort)));
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
            HttpServletResponse response) throws IOException {
        String exportedPackage = service.exportOfflinePackage(packageId);
        String safePackageId = packageId.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"package-offline-" + safePackageId + ".json\"");
        try (OutputStream output = response.getOutputStream()) {
            output.write(exportedPackage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
     * 触发包灰度/全量投影同步与发布。
     *
     * <p>权限：{@code package.publish}。
     */
    @PostMapping("/{packageId}/sync")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<PackageSyncResponse> syncPackage(
            @PathVariable String packageId,
            @RequestBody @Valid PackageSyncRequest request) {
        return ApiResult.ok(service.syncPackage(packageId, request));
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
        return ApiResult.ok(service.rollbackPackage(packageId, request));
    }

    /**
     * 获取当前租户下的所有激活同步目标列表。
     *
     * <p>权限：{@code package.read}。
     *
     * @return 包含同步目标列表的统一返回包络对象
     */
    @GetMapping("/sync-targets")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<List<SyncTarget>> listSyncTargets() {
        return ApiResult.ok(service.listSyncTargets());
    }
}
