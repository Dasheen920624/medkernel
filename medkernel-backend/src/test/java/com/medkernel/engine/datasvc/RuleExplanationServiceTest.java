package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 规则解释服务单元测试（DATASVC-01 PR2-b）。
 *
 * <p>验证：存在规则映射为 D1 已发布资产元数据 + 审计（FR-1/2/6）、不存在规则结构化 not-found（不泄漏内部）、
 * 上游不可用诚实降级不以伪造元数据伪装（FR-7/铁律 #1）。
 */
class RuleExplanationServiceTest {

    private RuleDefinitionRepository repo;
    private AuditRecorder auditRecorder;
    private RuleExplanationService service;

    @BeforeEach
    void setUp() {
        repo = mock(RuleDefinitionRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new RuleExplanationService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-xyz", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private RuleDefinition rule(String ruleId) {
        return new RuleDefinition(1L, ruleId, "tenant-1", "RULE-CODE-1", "高危药物配伍规则",
            RuleType.ORDER, RuleAuthoringMode.TEMPLATE, RuleRiskLevel.HIGH, 10, null, 0,
            RuleDefinitionStatus.PUBLISHED, "ver-9", null,
            Instant.parse("2026-06-01T00:00:00Z"), "author",
            Instant.parse("2026-06-10T00:00:00Z"), "editor", "trace-1");
    }

    @Test
    void explainRule_existingRule_mapsToD1MetadataWithAudit() {
        when(repo.findByRuleIdAndTenantId("R-1", "tenant-1")).thenReturn(Optional.of(rule("R-1")));

        RuleExplanation r = service.explainRule("R-1");

        assertThat(r.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(r.ruleId()).isEqualTo("R-1");
        assertThat(r.ruleCode()).isEqualTo("RULE-CODE-1");
        assertThat(r.ruleType()).isEqualTo("ORDER");
        assertThat(r.riskLevel()).isEqualTo("HIGH");
        assertThat(r.status()).isEqualTo("PUBLISHED");
        assertThat(r.activeVersionId()).isEqualTo("ver-9");
        assertThat(r.degraded()).isFalse();
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("rule_definition"), any(), contains("R-1"));
    }

    @Test
    void explainRule_missingRule_throwsStructuredNotFoundNotLeakingInternals() {
        when(repo.findByRuleIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.explainRule("NOPE"))
            .isInstanceOf(ApiException.class)
            .hasMessageNotContaining("SQL")
            .hasMessageNotContaining("Exception");
    }

    @Test
    void explainRule_upstreamDown_degradesHonestlyWithoutFakingMetadata() {
        when(repo.findByRuleIdAndTenantId(any(), any())).thenThrow(new RuntimeException("db down"));

        RuleExplanation r = service.explainRule("R-1");

        // 上游不可用：诚实标降级，元数据字段留空，不以伪造编码/版本伪装（铁律 #1）。
        assertThat(r.degraded()).isTrue();
        assertThat(r.degradeReason()).isNotBlank();
        assertThat(r.ruleCode()).isNull();
        assertThat(r.activeVersionId()).isNull();
    }
}
