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
@RequestMapping("/api/v1/projections/clinical-graph")
@DataScope(requireTenant = true)
public class ProjectionController {

    private final ProjectionSyncService service;

    public ProjectionController(ProjectionSyncService service) {
        this.service = service;
    }

    /**
     * 从关系库权威源重建临床图投影。
     */
    @PostMapping("/rebuild")
    @PreAuthorize("@perm.has('projection.rebuild')")
    public ApiResult<ProjectionRebuildResponse> rebuildClinicalGraph() {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        return ApiResult.ok(service.rebuildClinicalGraph(
            snapshot.orgScope().tenantId(),
            snapshot.userId() == null ? "system" : snapshot.userId(),
            snapshot.traceId()));
    }

    /**
     * 查询关系库权威源与临床图投影的一致性。
     */
    @GetMapping("/consistency")
    @PreAuthorize("@perm.has('projection.read')")
    public ApiResult<ProjectionConsistencyReport> checkClinicalGraphConsistency() {
        return ApiResult.ok(service.checkClinicalGraphConsistency(
            RequestContext.snapshot().orgScope().tenantId()));
    }
}
