package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 知识存在性服务（DATASVC-01 PR2-b，受控工具 {@code checkKnowledgeExistence} 上游）。
 *
 * <p>对知识身份编码回答「是否存在」，只读现有 {@code knowledge_identity}（engine-knowledge 所属，仅读不写、
 * 不违域归属）并强制租户隔离。落点：FR-1 读模型、FR-2 数据分级（D1 已发布资产元数据，无患者字段）、FR-6 全审计、
 * FR-7 诚实降级。<b>真实不存在＝ {@code exists=false} 且 {@code degraded=false}</b>（诚实回答非报错非降级，铁律 #1）；
 * 仅上游不可用时 {@code degraded=true}，未确认存在性不以「不存在」伪装。
 */
@Service
public class KnowledgeExistenceService {

    private final KnowledgeIdentityRepository knowledgeIdentityRepository;
    private final AuditRecorder auditRecorder;

    public KnowledgeExistenceService(KnowledgeIdentityRepository knowledgeIdentityRepository,
            AuditRecorder auditRecorder) {
        this.knowledgeIdentityRepository = knowledgeIdentityRepository;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 检查知识身份编码是否存在，返回 D1 最小元数据。上游不可用诚实降级。
     */
    @Transactional(readOnly = true)
    public KnowledgeExistence checkExistence(String identityCode) {
        String tenantId = requireCurrentTenant();

        boolean exists;
        String domain;
        String status;
        try {
            var found = knowledgeIdentityRepository.findByTenantIdAndIdentityCode(tenantId, identityCode);
            exists = found.isPresent();
            domain = found.map(ki -> nameOf(ki.domain())).orElse(null);
            status = found.map(ki -> nameOf(ki.status())).orElse(null);
        } catch (RuntimeException upstreamDown) {
            // 上游知识身份库不可用：诚实降级，未确认存在性，不以「不存在」伪装（铁律 #1）。
            auditRecorder.record(AuditAction.EXECUTE, "knowledge_identity", "check-knowledge-existence",
                "知识存在性检查上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return new KnowledgeExistence(identityCode, false, null, null,
                EngineDataLevel.D1, Instant.now(), true, "上游知识身份暂不可用，未确认存在性");
        }

        auditRecorder.record(AuditAction.EXECUTE, "knowledge_identity", "check-knowledge-existence",
            "检查知识存在性 D1 tenant=" + tenantId + " identityCode=" + identityCode + " exists=" + exists);
        return new KnowledgeExistence(identityCode, exists, domain, status,
            EngineDataLevel.D1, Instant.now(), false, null);
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
