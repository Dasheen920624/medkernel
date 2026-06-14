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
 * 引擎数据服务层 · 知识使用统计服务（DATASVC-01 PR1）。
 *
 * <p>把推荐卡引用知识的运行事实（{@code recommendation_source} 中 {@code source_type='KNOWLEDGE'} 子集）
 * 沉淀为 D2 去标识聚合读模型，服务端分页 + 默认时间窗 + 每次查询留审计。
 * 落点：FR-1 统一数据服务读模型（知识使用组）、FR-2 数据分级（恒 D2 去标识，无患者标识落地）、FR-6 全审计、
 * FR-7 诚实降级（空上游＝真实无数据不伪造；上游不可用诚实标 degraded 不以空数据伪装，铁律 #1）。
 */
@Service
public class KnowledgeUsageStatsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(90);

    private final KnowledgeUsageStatsRepository repo;
    private final AuditRecorder auditRecorder;

    public KnowledgeUsageStatsService(KnowledgeUsageStatsRepository repo, AuditRecorder auditRecorder) {
        this.repo = repo;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 查询知识使用统计（D2 去标识聚合，服务端分页）。{@code from}/{@code to} 为空时取默认 90 天窗口。
     */
    @Transactional(readOnly = true)
    public KnowledgeUsageStatsResponse queryKnowledgeUsage(Instant from, Instant to, int page, int size) {
        String tenantId = requireCurrentTenant();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (size <= 0) {
            safeSize = DEFAULT_PAGE_SIZE;
        }
        int safePage = Math.max(page, 0);
        Instant windowEnd = to != null ? to : Instant.now();
        Instant windowStart = from != null ? from : windowEnd.minus(DEFAULT_WINDOW);

        long total;
        List<KnowledgeUsageStat> rows;
        try {
            total = repo.countKnowledgeUsageGroups(tenantId, windowStart, windowEnd);
            rows = repo.aggregateKnowledgeUsage(tenantId, windowStart, windowEnd,
                safePage * safeSize, safeSize);
        } catch (RuntimeException upstreamDown) {
            // 上游推荐来源事实库不可用：诚实降级，不以空数据伪装真实统计（铁律 #1）。
            auditRecorder.record(AuditAction.EXECUTE, "recommendation_source", "knowledge-usage",
                "知识使用统计上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return new KnowledgeUsageStatsResponse(EngineDataLevel.D2, 0L, safePage, safeSize, List.of(),
                Instant.now(), true, "上游推荐来源事实暂不可用，未返回统计");
        }

        auditRecorder.record(AuditAction.EXECUTE, "recommendation_source", "knowledge-usage",
            "查询知识使用统计 D2 聚合 tenant=" + tenantId + " 分组=" + total + " page=" + safePage);
        return new KnowledgeUsageStatsResponse(EngineDataLevel.D2, total, safePage, safeSize, rows,
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
