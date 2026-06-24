package com.medkernel.engine.datasvc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;

import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.datasvc.export.EngineDataExportJob;
import com.medkernel.engine.datasvc.export.EngineDataExportService;
import com.medkernel.engine.datasvc.export.EngineDataExportSubmitRequest;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 引擎数据服务层只读统计与异步导出控制器（DATASVC-01）。
 *
 * <p>四入口（临床端嵌入 / 管理质控端 / CLI / MCP）共用同一后端受控合同；三组读模型：规则使用统计
 * {@code /api/v1/engine-data/rule-usage}、知识使用统计 {@code /api/v1/engine-data/knowledge-usage}
 * 与临床信号统计 {@code /api/v1/engine-data/clinical-signals}（均 D2 去标识聚合），CLI/MCP 共用的
 * 受控工具入口 {@code /api/v1/engine-data/tools}（目录 + 执行，FR-4 治理信封），以及经导出确认门禁
 * 控制的异步 CSV 导出 {@code /api/v1/engine-data/exports/*}（{@code engine-data.export}，FR-1）。读侧统一
 * {@code engine-data.read}，全线 {@link DataScope} 强多租户隔离；后端脱敏 + 数据分级 + 审计 + 诚实降级。
 */
@RestController
@RequestMapping("/api/v1/engine-data")
@DataScope(requireTenant = true)
public class EngineDataController {

    private final RuleUsageStatsService ruleUsageStatsService;
    private final KnowledgeUsageStatsService knowledgeUsageStatsService;
    private final ClinicalSignalsService clinicalSignalsService;
    private final ControlledToolService controlledToolService;
    private final EngineDataExportService engineDataExportService;

    public EngineDataController(RuleUsageStatsService ruleUsageStatsService,
            KnowledgeUsageStatsService knowledgeUsageStatsService,
            ClinicalSignalsService clinicalSignalsService,
            ControlledToolService controlledToolService,
            EngineDataExportService engineDataExportService) {
        this.ruleUsageStatsService = ruleUsageStatsService;
        this.knowledgeUsageStatsService = knowledgeUsageStatsService;
        this.clinicalSignalsService = clinicalSignalsService;
        this.controlledToolService = controlledToolService;
        this.engineDataExportService = engineDataExportService;
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

    /**
     * 受控工具目录（CLI/MCP 共用入口的工具发现，FR-3/4）：列出已登记受控工具的用途/数据级别/所需权限。
     */
    @GetMapping("/tools")
    @PreAuthorize("@perm.has('engine-data.read')")
    public ApiResult<List<ControlledToolDescriptor>> listTools() {
        return ApiResult.ok(controlledToolService.listTools());
    }

    /**
     * 执行受控工具（CLI/MCP 共用受控工具执行入口，FR-4/5/6）。
     *
     * <p>工具只能经此入口派发到既有受控服务执行，不直连库、不绕权限脱敏审计降级；返回必带 FR-4 治理信封
     * （traceId/数据级别/脱敏策略/来源版本/权限结果/降级状态/输出 hash）。未知工具返回结构化 404。
     */
    @PostMapping("/tools/{toolName}:execute")
    @PreAuthorize("@perm.hasAny('engine-data.read','knowledge.write')")
    public ApiResult<ToolExecutionEnvelope> executeTool(
            @PathVariable String toolName,
            @Valid @RequestBody ToolExecutionRequest request) {
        return ApiResult.ok(controlledToolService.execute(toolName, request));
    }

    /**
     * 提交异步导出作业。须先确认导出范围，立即返回 PENDING，随后轮询状态。
     */
    @PostMapping("/exports")
    @PreAuthorize("@perm.has('engine-data.export')")
    public ApiResult<EngineDataExportJob> submitExport(@Valid @RequestBody EngineDataExportSubmitRequest request) {
        return ApiResult.ok(engineDataExportService.submit(
            request.exportType(), request.windowDays(), request.confirmationId(), request.idempotencyKey()));
    }

    /**
     * 查询导出作业状态（按 jobCode）。
     */
    @GetMapping("/exports/{jobCode}")
    @PreAuthorize("@perm.has('engine-data.export')")
    public ApiResult<EngineDataExportJob> getExport(@PathVariable String jobCode) {
        return ApiResult.ok(engineDataExportService.get(jobCode));
    }

    /**
     * 分页列出当前租户导出作业台账。
     */
    @GetMapping("/exports")
    @PreAuthorize("@perm.has('engine-data.export')")
    public ApiResult<PageResponse<EngineDataExportJob>> listExports(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(engineDataExportService.listRecent(new PageRequest(page, size, null)));
    }

    /**
     * 取消未终态的导出作业。
     */
    @PostMapping("/exports/{jobCode}/cancel")
    @PreAuthorize("@perm.has('engine-data.export')")
    public ApiResult<EngineDataExportJob> cancelExport(@PathVariable String jobCode) {
        return ApiResult.ok(engineDataExportService.cancel(jobCode));
    }

    /**
     * 下载已成功导出作业的 CSV 文件。
     */
    @GetMapping("/exports/{jobCode}/download")
    @PreAuthorize("@perm.has('engine-data.export')")
    public void downloadExport(@PathVariable String jobCode, HttpServletResponse response) throws IOException {
        InputStream input = engineDataExportService.downloadFile(jobCode);
        String safeJobCode = jobCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"engine-data-export-" + safeJobCode + ".csv\"");
        try (input; OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }
}
