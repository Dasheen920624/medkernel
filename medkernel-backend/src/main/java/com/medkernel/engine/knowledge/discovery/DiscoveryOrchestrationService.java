package com.medkernel.engine.knowledge.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.KnowledgeAssetSchemaValidator;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 可信来源探索编排服务（LLM-06）。
 *
 * <p>受控检索 + 检索时点存证 + 来源核验：仅从已登记受控来源（KNOW-01）确定性检索（B0，无模型即可跑），
 * 每命中产一条带真实来源锚点的 DRAFT 候选信封（经 AIK-STD-01 校验闸），并写检索时点存证。
 * 无受控匹配诚实空态、上游不可用诚实降级，来源恒为真实注册片段，绝不臆造（铁律 #1）。
 */
@Service
public class DiscoveryOrchestrationService {

    /** 绑定模型网关能力码（LLM-01 注册的 knowledge.discovery，B0=确定性知识检索与关联基线）。 */
    private static final String CAPABILITY_CODE = "knowledge.discovery";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String EMPTY_CONTENT_MSG = "探索内容不能为空，禁止为空内容生成候选指纹";

    private final ControlledSourceSearchRepository searchRepository;
    private final DiscoveryRunRepository runRepository;
    private final KnowledgeAssetSchemaValidator schemaValidator;
    private final AuditRecorder auditRecorder;

    public DiscoveryOrchestrationService(
            ControlledSourceSearchRepository searchRepository,
            DiscoveryRunRepository runRepository,
            KnowledgeAssetSchemaValidator schemaValidator,
            AuditRecorder auditRecorder) {
        this.searchRepository = searchRepository;
        this.runRepository = runRepository;
        this.schemaValidator = schemaValidator;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 运行一次可信来源探索（确定性 B0）：仅检索受控源、产 DRAFT 候选、写检索时点存证。
     */
    @Transactional
    public DiscoveryResponse explore(DiscoveryRequest request) {
        String tenantId = requireCurrentTenant();
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "探索查询词不能为空");
        }
        String query = request.query().trim();
        String keyword = "%" + query.toLowerCase(Locale.ROOT) + "%";
        int limit = request.limit() == null ? DEFAULT_LIMIT : Math.min(Math.max(request.limit(), 1), MAX_LIMIT);
        String orgScope = tenantId;
        String runCode = UUID.randomUUID().toString();
        Instant executedAt = Instant.now();
        String createdBy = RequestContext.currentUserId().orElse(null);

        List<DiscoveryFragmentHit> hits;
        try {
            hits = searchRepository.searchControlledFragments(tenantId, keyword, limit);
        } catch (RuntimeException upstreamDown) {
            // 上游受控来源库不可用：诚实降级，不以空结果伪装真实探索（铁律 #1）。
            DiscoveryRun degraded = persistRun(tenantId, runCode, query, executedAt, "[]",
                0, 0, computeResultHash(query, List.of()), DiscoveryRunStatus.DEGRADED, true, createdBy);
            auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_discovery_run", runCode,
                "可信来源探索上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return toResponse(degraded, List.of());
        }

        List<KnowledgeAssetEnvelope> candidates = new ArrayList<>();
        for (DiscoveryFragmentHit hit : hits) {
            KnowledgeAssetEnvelope candidate = toCandidate(hit, runCode, orgScope, query);
            schemaValidator.validate(candidate);
            candidates.add(candidate);
        }

        DiscoveryRunStatus status = hits.isEmpty() ? DiscoveryRunStatus.EMPTY : DiscoveryRunStatus.SUCCEEDED;
        String sourceSnapshot = buildSourceSnapshot(hits);
        String resultHash = computeResultHash(query, candidates);
        DiscoveryRun run = persistRun(tenantId, runCode, query, executedAt, sourceSnapshot,
            hits.size(), candidates.size(), resultHash, status, false, createdBy);
        auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_discovery_run", runCode,
            "运行可信来源探索 query=" + query + " 命中=" + hits.size() + " 候选=" + candidates.size()
                + " 结果指纹=" + resultHash);
        return toResponse(run, candidates);
    }

    /**
     * 分页查询探索运行台账（FR-2 复查「当时看到什么」）。
     */
    @Transactional(readOnly = true)
    public PageResponse<DiscoveryRun> listRuns(int page, int size) {
        String tenantId = requireCurrentTenant();
        PageRequest pageRequest = new PageRequest(page, size, null);
        long total = runRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(
            runRepository.pageByTenantId(tenantId, pageRequest.offset(), pageRequest.safeSize()),
            pageRequest,
            total
        );
    }

    private KnowledgeAssetEnvelope toCandidate(
            DiscoveryFragmentHit hit, String runCode, String orgScope, String query) {
        String sourceRef = hit.sourceCode() + ":" + hit.versionNo() + ":" + hit.anchorPath();
        String payload = hit.textExcerpt();
        String contentHash = Sha256ContentHash.sha256(payload, EMPTY_CONTENT_MSG);
        return new KnowledgeAssetEnvelope(
            VersionedAssetType.KNOWLEDGE,
            "discovery:" + sourceRef,
            query + " · " + hit.anchorLabel(),
            runCode,
            List.of(new AssetSourceRef(sourceRef, hit.authorityLevel())),
            hit.authorityLevel(),
            null,
            null,
            KnowledgeRiskLevel.MEDIUM,
            orgScope,
            contentHash,
            payload,
            AssetVersionStatus.DRAFT);
    }

    /** 结果集真实指纹：query + 各候选来源锚点:内容指纹有序拼接的 SHA-256（B0 确定性可复算核验）。 */
    private String computeResultHash(String query, List<KnowledgeAssetEnvelope> candidates) {
        String canonical = query + "\n" + candidates.stream()
            .map(c -> c.sources().get(0).sourceRef() + ":" + c.contentHash())
            .collect(Collectors.joining("\n"));
        return Sha256ContentHash.sha256(canonical, EMPTY_CONTENT_MSG);
    }

    /** 当时检索的源版本快照（去重，复查「当时看到什么」），不复制 fragment 正文。 */
    private String buildSourceSnapshot(List<DiscoveryFragmentHit> hits) {
        Set<String> entries = new LinkedHashSet<>();
        for (DiscoveryFragmentHit hit : hits) {
            entries.add("{\"sourceCode\":\"" + escape(hit.sourceCode())
                + "\",\"versionNo\":\"" + escape(hit.versionNo())
                + "\",\"versionContentHash\":\"" + escape(hit.versionContentHash()) + "\"}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private DiscoveryRun persistRun(
            String tenantId, String runCode, String query, Instant executedAt, String sourceSnapshot,
            int hitCount, int candidateCount, String resultHash, DiscoveryRunStatus status,
            boolean degraded, String createdBy) {
        DiscoveryRun run = new DiscoveryRun(null, tenantId, runCode, query, CAPABILITY_CODE, executedAt,
            sourceSnapshot, hitCount, candidateCount, resultHash, status, degraded, createdBy, Instant.now());
        return runRepository.save(run);
    }

    private DiscoveryResponse toResponse(DiscoveryRun run, List<KnowledgeAssetEnvelope> candidates) {
        return new DiscoveryResponse(run.runCode(), run.executedAt(), run.status(), run.degraded(),
            run.hitCount(), run.candidateCount(), run.resultHash(), candidates);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
