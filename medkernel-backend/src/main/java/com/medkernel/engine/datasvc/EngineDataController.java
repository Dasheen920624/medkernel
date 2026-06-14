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
 * <p>四入口（临床端嵌入 / 管理质控端 / CLI / MCP）共用同一后端受控合同；当前三组：规则使用统计
 * {@code /api/v1/engine-data/rule-usage}、知识使用统计 {@code /api/v1/engine-data/knowledge-usage}
 * 与临床信号统计 {@code /api/v1/engine-data/clinical-signals}（均 D2 去标识聚合）。读侧统一
 * {@code engine-data.read}，全线 {@link DataScope} 强多租户隔离；后端脱敏 + 数据分级 + 审计 + 诚实降级。
 */
@RestController
@RequestMapping("/api/v1/engine-data")
@DataScope(requireTenant = true)
public class EngineDataController {

    private final RuleUsageStatsService ruleUsageStatsService;
    private final KnowledgeUsageStatsService knowledgeUsageStatsService;
    private final ClinicalSignalsService clinicalSignalsService;

    public EngineDataController(RuleUsageStatsService ruleUsageStatsService,
            KnowledgeUsageStatsService knowledgeUsageStatsService,
            ClinicalSignalsService clinicalSignalsService) {
        this.ruleUsageStatsService = ruleUsageStatsService;
        this.knowledgeUsageStatsService = knowledgeUsageStatsService;
        this.clinicalSignalsService = clinicalSignalsService;
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

    /**
     * 知识使用统计（D2 去标识聚合，服务端分页 + 时间窗筛选）。
     *
     * <p>统计推荐卡真实引用知识源的运行事实（{@code recommendation_source} 中 KNOWLEDGE 子集），
     * 按知识引用键聚合被引用次数与去重卡片数；空上游＝真实无数据，上游不可用诚实降级（铁律 #1）。
     */
    @GetMapping("/knowledge-usage")
    @PreAuthorize("@perm.has('engine-data.read')")
    public ApiResult<KnowledgeUsageStatsResponse> knowledgeUsage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(knowledgeUsageStatsService.queryKnowledgeUsage(from, to, page, size));
    }

    /**
     * 临床信号统计（D2 去标识聚合，服务端分页 + 时间窗筛选）。
     *
     * <p>统计推荐引擎真实投递的 CDSS 决策信号运行事实（{@code recommendation_card}），按信号类别聚合
     * 信号总数 / 高危数 / 真实记录的采纳·驳回计数；卡级聚合不触碰患者字段（D2），上游不可用诚实降级（铁律 #1）。
     */
    @GetMapping("/clinical-signals")
    @PreAuthorize("@perm.has('engine-data.read')")
    public ApiResult<ClinicalSignalsResponse> clinicalSignals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(clinicalSignalsService.queryClinicalSignals(from, to, page, size));
    }
}
