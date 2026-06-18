package com.medkernel.engine.knowledge.production;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
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
    private final ObjectMapper objectMapper;

    public CandidateProvenanceService(KnowledgeProductionCandidateRepository candidateRepository,
                                      KnowledgeProductionJobRepository jobRepository,
                                      ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    /** 解析一组候选引用的生产来源；无血缘行 / 跨租户引用诚实不返回（强租户隔离）。 */
    @Transactional(readOnly = true)
    public List<CandidateProvenanceView> resolve(Collection<String> candidateRefs) {
        String tenantId = requireCurrentTenant();
        if (candidateRefs == null || candidateRefs.isEmpty()) {
            return List.of();
        }
        if (candidateRefs.size() > CandidateProvenanceRequest.MAX_CANDIDATE_REFS) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "候选来源溯源一次最多查询 " + CandidateProvenanceRequest.MAX_CANDIDATE_REFS + " 条");
        }
        if (candidateRefs.stream().anyMatch(ref -> ref == null || ref.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "候选来源溯源引用不能为空");
        }
        List<String> normalizedRefs = candidateRefs.stream()
            .map(String::trim)
            .distinct()
            .toList();
        List<KnowledgeProductionCandidate> rows =
            candidateRepository.findByTenantIdAndCandidateRefIn(tenantId, normalizedRefs);
        Map<String, KnowledgeProductionJob> jobsByCode = new HashMap<>();
        List<CandidateProvenanceView> views = new ArrayList<>();
        for (KnowledgeProductionCandidate row : rows) {
            KnowledgeProductionJob job = jobsByCode.computeIfAbsent(row.jobCode(),
                code -> jobRepository.findByTenantIdAndJobCode(tenantId, code).orElse(null));
            if (job == null) {
                // 血缘行存在但归属 job 缺失（异常态）：诚实跳过不臆造来源。
                continue;
            }
            views.add(CandidateProvenanceView.from(row, job, explainEvidence(row.explainJson())));
        }
        return views;
    }

    private CandidateProvenanceView.ExplainEvidence explainEvidence(String explainJson) {
        if (explainJson == null || explainJson.isBlank()) {
            return CandidateProvenanceView.ExplainEvidence.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(explainJson);
            return new CandidateProvenanceView.ExplainEvidence(
                text(root, "modelTaskId"),
                text(root, "modelMode"),
                text(root, "modelVersion"),
                text(root, "promptVersion"),
                text(root, "toolVersion"),
                jsonText(root.get("sourceCitations")),
                number(root, "confidence"),
                bool(root, "fallbackUsed"),
                text(root, "fallbackReason"));
        } catch (JsonProcessingException ignored) {
            return CandidateProvenanceView.ExplainEvidence.empty();
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String jsonText(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private Double number(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isNumber() ? node.asDouble() : null;
    }

    private Boolean bool(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isBoolean() ? node.asBoolean() : null;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
