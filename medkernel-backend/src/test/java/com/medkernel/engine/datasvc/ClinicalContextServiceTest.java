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

import com.medkernel.engine.embed.EmbedLaunchToken;
import com.medkernel.engine.embed.EmbedLaunchTokenRepository;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 临床上下文服务单元测试（DATASVC-01 PR2-d，D4 须绑临床 launch 令牌）。
 *
 * <p>验证：有效令牌返回 D4 授权上下文且**患者引用经不可逆 hash 脱敏不泄漏原始患者字段**（视角 11 / FR-2）；
 * 令牌无效/过期/越租户＝诚实拒绝不返回临床数据；上游不可用诚实降级不以「未授权」伪装（FR-7/铁律 #1）。
 */
class ClinicalContextServiceTest {

    private EmbedLaunchTokenRepository repo;
    private AuditRecorder auditRecorder;
    private ClinicalContextService service;

    @BeforeEach
    void setUp() {
        repo = mock(EmbedLaunchTokenRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ClinicalContextService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-xyz", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private EmbedLaunchToken token(String tenantId, String status, Instant expiredAt) {
        return new EmbedLaunchToken(1L, "tok-1", tenantId, "user-9", "clinical-user",
            "P-123456", "E-999", "order-sign", status, expiredAt,
            Instant.parse("2026-06-14T00:00:00Z"), "creator",
            Instant.parse("2026-06-14T00:00:00Z"), "editor", "trace-1",
            "IFRAME", "order-sign", "hook-1", null, "https://his.example.org");
    }

    @Test
    void explainContext_validToken_returnsAuthorizedD4ContextWithMaskedPatientRef() {
        when(repo.findByToken("tok-1"))
            .thenReturn(Optional.of(token("tenant-1", "USED", Instant.now().plusSeconds(3600))));

        ClinicalContextExplanation result = service.explainContext("tok-1", "AI 解释临床会话授权范围");

        assertThat(result.authorized()).isTrue();
        assertThat(result.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(result.triggerPoint()).isEqualTo("order-sign");
        assertThat(result.roleCode()).isEqualTo("clinical-user");
        assertThat(result.degraded()).isFalse();
        // 患者/就诊引用须脱敏：不得出现原始标识。
        assertThat(result.patientRef()).isNotBlank().doesNotContain("P-123456");
        assertThat(result.encounterRef()).isNotBlank().doesNotContain("E-999");
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("embed_launch_token"), any(),
            contains("AI 解释临床会话授权范围"));
    }

    @Test
    void explainContext_invalidToken_deniesHonestlyWithoutClinicalData() {
        when(repo.findByToken(any())).thenReturn(Optional.empty());

        ClinicalContextExplanation result = service.explainContext("nope", "用途");

        assertThat(result.authorized()).isFalse();
        assertThat(result.reason()).isNotBlank();
        // 拒绝时绝不返回任何临床字段。
        assertThat(result.triggerPoint()).isNull();
        assertThat(result.patientRef()).isNull();
    }

    @Test
    void explainContext_expiredToken_deniesHonestly() {
        when(repo.findByToken("tok-1"))
            .thenReturn(Optional.of(token("tenant-1", "USED", Instant.now().minusSeconds(60))));

        ClinicalContextExplanation result = service.explainContext("tok-1", "用途");

        assertThat(result.authorized()).isFalse();
        assertThat(result.patientRef()).isNull();
    }

    @Test
    void explainContext_crossTenantToken_deniesWithoutLeaking() {
        when(repo.findByToken("tok-1"))
            .thenReturn(Optional.of(token("other-tenant", "USED", Instant.now().plusSeconds(3600))));

        ClinicalContextExplanation result = service.explainContext("tok-1", "用途");

        assertThat(result.authorized()).isFalse();
        assertThat(result.triggerPoint()).isNull();
        assertThat(result.patientRef()).isNull();
    }

    @Test
    void explainContext_upstreamDown_degradesHonestlyNotFakingUnauthorized() {
        when(repo.findByToken(any())).thenThrow(new RuntimeException("db down"));

        ClinicalContextExplanation result = service.explainContext("tok-1", "用途");

        assertThat(result.degraded()).isTrue();
        assertThat(result.authorized()).isFalse();
        assertThat(result.degradeReason()).isNotBlank();
    }
}
