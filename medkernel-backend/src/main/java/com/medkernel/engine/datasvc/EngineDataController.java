package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * 引擎数据服务层只读统计控制器（DATASVC-01 PR1）。
 *
 * <p>四入口（临床端嵌入 / 管理质控端 / CLI / MCP）共用同一后端受控合同的第一组：规则使用统计
 * {@code /api/v1/engine-data/rule-usage}（D2 去标识聚合）。读侧统一 {@code engine-data.read}，
 * 全线 {@link DataScope} 强多租户隔离；后端脱敏 + 数据分级 + 审计 + 诚实降级。
 */
@RestController
@RequestMapping("/api/v1/engine-data")
@DataScope(requireTenant = true)
public class EngineDataController {

    private final RuleUsageStatsService ruleUsageStatsService;

    public EngineDataController(RuleUsageStatsService ruleUsageStatsService) {
        this.ruleUsageStatsService = ruleUsageStatsService;
    }

    /**
     * 规则使用统计（D2 去标识聚合，服务端分页 + 时间窗筛选）。
     */
    @GetMapping("/rule-usage")
    @PreAuthorize("@perm.has('engine-data.read')")
    public ApiResult<RuleUsageStatsResponse> ruleUsage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(ruleUsageStatsService.queryRuleUsage(from, to, page, size));
    }
}
