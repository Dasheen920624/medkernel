package com.medkernel.engine.datasvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 规则使用统计服务（DATASVC-01）。
 *
 * <p>把规则引擎运行事实沉淀为 D2 去标识聚合读模型，服务端分页 + 默认时间窗 + 每次查询留审计。
 * 落点：FR-1 统一数据服务读模型、FR-2 数据分级（恒 D2 去标识，无患者标识落地）、FR-6 全审计、
 * FR-7 诚实降级（空上游＝真实无数据不伪造；上游不可用诚实标 degraded 不以空数据伪装，铁律 #1）。
 */
@Service
public class RuleUsageStatsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(90);

    private final RuleUsageStatsRepository repo;
    private final AuditRecorder auditRecorder;

    public RuleUsageStatsService(RuleUsageStatsRepository repo, AuditRecorder auditRecorder) {
        this.repo = repo;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 查询规则使用统计（D2 去标识聚合，服务端分页）。{@code from}/{@code to} 为空时取默认 90 天窗口。
     */
    @Transactional(readOnly = true)
    public RuleUsageStatsResponse queryRuleUsage(Instant from, Instant to, int page, int size) {
        String tenantId = requireCurrentTenant();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (size <= 0) {
            safeSize = DEFAULT_PAGE_SIZE;
        }
        int safePage = Math.max(page, 0);
        Instant windowEnd = to != null ? to : Instant.now();
        Instant windowStart = from != null ? from : windowEnd.minus(DEFAULT_WINDOW);

        long total;
        List<RuleUsageStat> rows;
        try {
            total = repo.countRuleUsageGroups(tenantId, windowStart, windowEnd);
            rows = repo.aggregateRuleUsage(tenantId, windowStart, windowEnd,
                safePage * safeSize, safeSize);
        } catch (RuntimeException upstreamDown) {
            // 上游运行事实库不可用：诚实降级，不以空数据伪装真实统计（铁律 #1）。
            auditRecorder.record(AuditAction.EXECUTE, "rule_execution_log", "rule-usage",
                "规则使用统计上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return new RuleUsageStatsResponse(EngineDataLevel.D2, 0L, safePage, safeSize, List.of(),
                Instant.now(), true, "上游规则运行事实暂不可用，未返回统计");
        }

        auditRecorder.record(AuditAction.EXECUTE, "rule_execution_log", "rule-usage",
            "查询规则使用统计 D2 聚合 tenant=" + tenantId + " 分组=" + total + " page=" + safePage);
        return new RuleUsageStatsResponse(EngineDataLevel.D2, total, safePage, safeSize, rows,
            Instant.now(), false, null);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
