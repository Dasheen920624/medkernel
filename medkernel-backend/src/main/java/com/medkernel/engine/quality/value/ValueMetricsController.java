package com.medkernel.engine.quality.value;

import java.time.Instant;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPT-08 价值指标与 ROI 看板 API。
 *
 * <p>提供只读聚合和下钻回溯入口，所有数据均按当前租户上下文过滤。
 */
@RestController
@RequestMapping("/api/v1/engine/value-metrics")
@DataScope(requireTenant = true)
public class ValueMetricsController {
    private final ValueMetricsService service;

    public ValueMetricsController(ValueMetricsService service) {
        this.service = service;
    }

    /**
     * 查询当前租户下 6 类价值指标的受控口径聚合结果。
     */
    @GetMapping
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<ValueMetricSummaryResponse> summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hospitalId,
            @RequestParam(required = false) String campusId) {
        return ApiResult.ok(service.summary(new ValueMetricFilter(from, to, departmentId, hospitalId, campusId)));
    }

    /**
     * 按指标代码下钻到真实来源事实。
     */
    @GetMapping("/{metricCode}/drilldown")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<ValueMetricDrilldownResponse> drilldown(
            @PathVariable ValueMetricCode metricCode,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hospitalId,
            @RequestParam(required = false) String campusId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest req = new PageRequest(page, size, null);
        return ApiResult.ok(service.drilldown(
            metricCode, new ValueMetricFilter(from, to, departmentId, hospitalId, campusId),
            req.offset(), req.safeSize()));
    }
}
