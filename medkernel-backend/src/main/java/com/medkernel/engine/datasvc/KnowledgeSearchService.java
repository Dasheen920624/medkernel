package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 知识检索服务（DATASVC-01，受控工具 {@code searchKnowledge} 上游）。
 *
 * <p>按关键词只读检索 {@code knowledge_identity}（engine-knowledge 所属，仅读不写、不违域归属）并强制租户隔离，
 * 映射为 D1 已发布资产元数据命中列表，服务端分页。落点：FR-1 读模型、FR-2 数据分级（D1 无患者字段）、FR-6 审计、
 * FR-7 诚实降级。真实无匹配＝诚实空结果（非降级）；上游不可用诚实标 {@code degraded}，空结果不伪装真实无匹配（铁律 #1）。
 */
@Service
public class KnowledgeSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final KnowledgeIdentityRepository knowledgeIdentityRepository;
    private final AuditRecorder auditRecorder;

    public KnowledgeSearchService(KnowledgeIdentityRepository knowledgeIdentityRepository,
            AuditRecorder auditRecorder) {
        this.knowledgeIdentityRepository = knowledgeIdentityRepository;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 按关键词检索知识身份的 D1 元数据（服务端分页）。上游不可用诚实降级。
     */
    @Transactional(readOnly = true)
    public KnowledgeSearchResult search(String keyword, int page, int size) {
        String tenantId = requireCurrentTenant();
        String like = normalizeKeyword(keyword);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (size <= 0) {
            safeSize = DEFAULT_PAGE_SIZE;
        }
        int safePage = Math.max(page, 0);

        long total;
        List<KnowledgeSearchHit> hits;
        try {
            total = knowledgeIdentityRepository.countByFilter(tenantId, null, null, null, like);
            hits = knowledgeIdentityRepository
                .pageByFilter(tenantId, null, null, null, like, safePage * safeSize, safeSize)
                .stream()
                .map(this::toHit)
                .toList();
        } catch (RuntimeException upstreamDown) {
            // 上游知识身份库不可用：诚实降级，空结果不伪装真实无匹配（铁律 #1）。
            auditRecorder.record(AuditAction.EXECUTE, "knowledge_identity", "search-knowledge",
                "知识检索上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return new KnowledgeSearchResult(EngineDataLevel.D1, 0L, safePage, safeSize, List.of(),
                Instant.now(), true, "上游知识身份暂不可用，未返回检索结果");
        }

        auditRecorder.record(AuditAction.EXECUTE, "knowledge_identity", "search-knowledge",
            "检索知识身份 D1 tenant=" + tenantId + " 关键词=" + keyword + " 命中=" + total);
        return new KnowledgeSearchResult(EngineDataLevel.D1, total, safePage, safeSize, hits,
            Instant.now(), false, null);
    }

    private KnowledgeSearchHit toHit(KnowledgeIdentity identity) {
        return new KnowledgeSearchHit(identity.identityCode(), identity.subject(),
            nameOf(identity.domain()), nameOf(identity.status()));
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        return "%" + trimmed + "%";
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
