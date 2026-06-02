package com.medkernel.engine.projection;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * 关系库权威源与图投影同步控制器。
 */
@RestController
@RequestMapping("/api/v1/projections")
@DataScope(requireTenant = true)
public class ProjectionController {

    private final ProjectionSyncService service;

    public ProjectionController(ProjectionSyncService service) {
        this.service = service;
    }

    /**
     * 从关系库权威源重建临床图投影。
     */
    @PostMapping("/clinical-graph/rebuild")
    @PreAuthorize("@perm.has('projection.rebuild')")
    public ApiResult<ProjectionRebuildResponse> rebuildClinicalGraph() {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        return ApiResult.ok(service.rebuildClinicalGraph(
            snapshot.orgScope().tenantId(),
            snapshot.userId() == null ? "system" : snapshot.userId(),
            snapshot.traceId()));
    }

    /**
     * 查询临床图投影运行状态。
     */
    @GetMapping("/clinical-graph/status")
    @PreAuthorize("@perm.has('projection.read')")
    public ApiResult<ProjectionRuntimeStatusResponse> status() {
        return ApiResult.ok(service.runtimeStatus(
            RequestContext.snapshot().orgScope().tenantId()));
    }

    /**
     * 查询关系库权威源与临床图投影的一致性。
     */
    @GetMapping("/clinical-graph/consistency")
    @PreAuthorize("@perm.has('projection.read')")
    public ApiResult<ProjectionConsistencyReport> checkClinicalGraphConsistency() {
        return ApiResult.ok(service.checkClinicalGraphConsistency(
            RequestContext.snapshot().orgScope().tenantId()));
    }

    /**
     * 从关系库权威知识资产重建知识图投影。
     */
    @PostMapping("/knowledge-graph/rebuild")
    @PreAuthorize("@perm.has('projection.rebuild')")
    public ApiResult<ProjectionRebuildResponse> rebuildKnowledgeGraph() {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        return ApiResult.ok(service.rebuildKnowledgeGraph(
            snapshot.orgScope().tenantId(),
            snapshot.userId() == null ? "system" : snapshot.userId(),
            snapshot.traceId()));
    }

    /**
     * 查询关系库权威知识资产与知识图投影的一致性。
     */
    @GetMapping("/knowledge-graph/consistency")
    @PreAuthorize("@perm.has('projection.read')")
    public ApiResult<ProjectionConsistencyReport> checkKnowledgeGraphConsistency() {
        return ApiResult.ok(service.checkKnowledgeGraphConsistency(
            RequestContext.snapshot().orgScope().tenantId()));
    }

    /**
     * 从关系库权威知识资产重建搜索投影。
     */
    @PostMapping("/knowledge-search/rebuild")
    @PreAuthorize("@perm.has('projection.rebuild')")
    public ApiResult<ProjectionRebuildResponse> rebuildKnowledgeSearch() {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        return ApiResult.ok(service.rebuildKnowledgeSearch(
            snapshot.orgScope().tenantId(),
            snapshot.userId() == null ? "system" : snapshot.userId(),
            snapshot.traceId()));
    }

    /**
     * 查询关系库权威知识资产与搜索投影的一致性。
     */
    @GetMapping("/knowledge-search/consistency")
    @PreAuthorize("@perm.has('projection.read')")
    public ApiResult<ProjectionConsistencyReport> checkKnowledgeSearchConsistency() {
        return ApiResult.ok(service.checkKnowledgeSearchConsistency(
            RequestContext.snapshot().orgScope().tenantId()));
    }
}
