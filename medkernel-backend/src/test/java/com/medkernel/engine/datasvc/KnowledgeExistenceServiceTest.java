package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 知识存在性服务单元测试（DATASVC-01）。
 *
 * <p>验证：存在返回 {@code exists=true} + D1 元数据 + 审计、<b>真实不存在诚实返回 {@code exists=false} 且
 * 非降级非报错</b>（铁律 #1）、仅上游不可用时诚实标降级（不以「不存在」伪装）。
 */
class KnowledgeExistenceServiceTest {

    private KnowledgeIdentityRepository repo;
    private AuditRecorder auditRecorder;
    private KnowledgeExistenceService service;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeIdentityRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new KnowledgeExistenceService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-xyz", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeIdentity identity(String code) {
        return new KnowledgeIdentity(1L, "tenant-1", code, KnowledgeDomain.GUIDELINE, "诊疗指南主题",
            "spec-1", "描述", KnowledgeIdentityStatus.ACTIVE, 7L,
            Instant.parse("2026-06-01T00:00:00Z"), "creator",
            Instant.parse("2026-06-10T00:00:00Z"), "editor");
    }

    @Test
    void checkExistence_present_returnsExistsTrueWithD1Metadata() {
        when(repo.findByTenantIdAndIdentityCode("tenant-1", "K-1")).thenReturn(Optional.of(identity("K-1")));

        KnowledgeExistence r = service.checkExistence("K-1");

        assertThat(r.exists()).isTrue();
        assertThat(r.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(r.domain()).isEqualTo("GUIDELINE");
        assertThat(r.status()).isEqualTo("ACTIVE");
        assertThat(r.degraded()).isFalse();
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("knowledge_identity"), any(), contains("K-1"));
    }

    @Test
    void checkExistence_absent_returnsExistsFalseHonestlyNotDegradedNotError() {
        when(repo.findByTenantIdAndIdentityCode(any(), any())).thenReturn(Optional.empty());

        KnowledgeExistence r = service.checkExistence("GONE");

        // 真实不存在＝诚实回答，既不是降级也不是报错（铁律 #1）。
        assertThat(r.exists()).isFalse();
        assertThat(r.degraded()).isFalse();
        assertThat(r.identityCode()).isEqualTo("GONE");
    }

    @Test
    void checkExistence_upstreamDown_degradesHonestlyNotFakingAbsence() {
        when(repo.findByTenantIdAndIdentityCode(any(), any())).thenThrow(new RuntimeException("db down"));

        KnowledgeExistence r = service.checkExistence("K-1");

        // 上游不可用：诚实标降级、未确认存在性，不以「不存在」伪装真实状态。
        assertThat(r.degraded()).isTrue();
        assertThat(r.exists()).isFalse();
        assertThat(r.degradeReason()).isNotBlank();
    }
}
