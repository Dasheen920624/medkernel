package com.medkernel.engine.knowledge.production;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选生产来源溯源服务（AIK-STD-12 PR1）。
 *
 * <p>审核台候选经生产血缘反查 AI 工厂来源：候选引用 → {@code mk_knowledge_production_candidate} →
 * job → 生产器/管道/模型策略。旁挂只读查询，<b>不改既有候选审核响应</b>（零前端破坏）。
 * 无血缘行的候选（手建，非经生产 job）诚实不返回——审核台据缺省判「非工厂候选」（铁律 #1 不臆造）。
 */
@Service
public class CandidateProvenanceService {

    private final KnowledgeProductionCandidateRepository candidateRepository;
    private final KnowledgeProductionJobRepository jobRepository;

    public CandidateProvenanceService(KnowledgeProductionCandidateRepository candidateRepository,
                                      KnowledgeProductionJobRepository jobRepository) {
        this.candidateRepository = candidateRepository;
        this.jobRepository = jobRepository;
    }

    /** 解析一组候选引用的生产来源；无血缘行 / 跨租户引用诚实不返回（强租户隔离）。 */
    @Transactional(readOnly = true)
    public List<CandidateProvenanceView> resolve(Collection<String> candidateRefs) {
        String tenantId = requireCurrentTenant();
        if (candidateRefs == null || candidateRefs.isEmpty()) {
            return List.of();
        }
        List<KnowledgeProductionCandidate> rows =
            candidateRepository.findByTenantIdAndCandidateRefIn(tenantId, candidateRefs);
        Map<String, KnowledgeProductionJob> jobsByCode = new HashMap<>();
        List<CandidateProvenanceView> views = new ArrayList<>();
        for (KnowledgeProductionCandidate row : rows) {
            KnowledgeProductionJob job = jobsByCode.computeIfAbsent(row.jobCode(),
                code -> jobRepository.findByTenantIdAndJobCode(tenantId, code).orElse(null));
            if (job == null) {
                // 血缘行存在但归属 job 缺失（异常态）：诚实跳过不臆造来源。
                continue;
            }
            views.add(CandidateProvenanceView.from(row, job));
        }
        return views;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
