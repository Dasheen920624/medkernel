package com.medkernel.engine.quality.dashboard;

import java.time.Instant;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SVC-QUALITY-01 质控驾驶舱 API。
 *
 * <p>提供院级聚合、下钻证据与预警列表，所有结果按当前租户上下文过滤。
 */
@RestController
@RequestMapping("/api/v1/engine/quality")
@DataScope(requireTenant = true)
public class QualityDashboardController {
    private final QualityDashboardService service;

    public QualityDashboardController(QualityDashboardService service) {
        this.service = service;
    }

    /**
     * 查询质控驾驶舱聚合视图，并幂等刷新确定性预警 read-model。
     */
    @GetMapping("/dashboard")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<QualityDashboardResponse> dashboard(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String departmentId) {
        return ApiResult.ok(service.dashboard(new QualityDashboardFilter(from, to, departmentId)));
    }

    /**
     * 按来源类型下钻到真实质控证据。
     */
    @GetMapping("/dashboard/drilldown")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<QualityDashboardDrilldownResponse> drilldown(
            @RequestParam(defaultValue = "FINDING") QualityDashboardDrilldownType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest req = new PageRequest(page, size, null);
        return ApiResult.ok(service.drilldown(
            new QualityDashboardFilter(from, to, departmentId), type, req.offset(), req.safeSize()));
    }

    /**
     * 查询质控预警列表。
     */
    @GetMapping("/alerts")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<QualityDashboardAlertsResponse> alerts(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) QualityDashboardAlertStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest req = new PageRequest(page, size, null);
        return ApiResult.ok(service.alerts(
            new QualityDashboardAlertFilter(from, to, departmentId, status), req.offset(), req.safeSize()));
    }
}
