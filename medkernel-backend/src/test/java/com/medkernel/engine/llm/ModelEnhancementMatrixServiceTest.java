package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 全业务模型增强接入矩阵服务单元测试（LLM-05）。
 *
 * <p>验证：矩阵台账列举（FR-1）、B0 前置门禁（FR-2，无 B0 不得上线）、统一经网关接入校验
 * （FR-3，能力码须已在网关登记）、覆盖核查缺口诚实标注（FR-4，不虚报覆盖）。
 */
class ModelEnhancementMatrixServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");

    private ModelEnhancementMatrixRepository matrixRepo;
    private ModelCapabilityDefinitionRepository definitionRepo;
    private AuditRecorder auditRecorder;
    private ModelEnhancementMatrixService service;

    @BeforeEach
    void setUp() {
        matrixRepo = mock(ModelEnhancementMatrixRepository.class);
        definitionRepo = mock(ModelCapabilityDefinitionRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelEnhancementMatrixService(matrixRepo, definitionRepo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "platform-001"));
        when(matrixRepo.save(any(ModelEnhancementMatrix.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ModelEnhancementMatrix activeEntry(String point, String capability) {
        return new ModelEnhancementMatrix(1L, point, point + " 业务", capability,
            "确定性 B0 基线", "ACTIVE", "Y", 10, NOW, "system", NOW, "system");
    }

    private ModelEnhancementMatrix pendingGap(String point) {
        return new ModelEnhancementMatrix(2L, point, point + " 业务", null,
            null, "PENDING", "Y", 90, NOW, "system", NOW, "system");
    }

    private ModelCapabilityDefinition enabledDefinition(String code) {
        return new ModelCapabilityDefinition(code, "名称", "描述", "分类", "Y", 10, NOW, "system", NOW, "system");
    }

    @Test
    void listMatrix_returnsLedgerEntries() {
        when(matrixRepo.findAllByOrderBySortOrderAscBusinessPointAsc())
            .thenReturn(List.of(activeEntry("rule", "rule.draft"), pendingGap("nursing")));

        List<ModelEnhancementMatrixResponse> matrix = service.listMatrix();

        assertThat(matrix).extracting(ModelEnhancementMatrixResponse::businessPoint)
            .containsExactly("rule", "nursing");
    }

    @Test
    void coverageReport_marksGapsHonestlyWithoutInflatingCoverage() {
        when(matrixRepo.findAllByOrderBySortOrderAscBusinessPointAsc())
            .thenReturn(List.of(
                activeEntry("rule", "rule.draft"),
                activeEntry("pathway", "pathway.draft"),
                pendingGap("nursing"),
                pendingGap("report")));

        EnhancementCoverageReport report = service.coverageReport();

        assertThat(report.total()).isEqualTo(4);
        assertThat(report.active()).isEqualTo(2);
        assertThat(report.gaps()).isEqualTo(2);
        assertThat(report.gapEntries()).extracting(ModelEnhancementMatrixResponse::businessPoint)
            .containsExactlyInAnyOrder("nursing", "report");
    }

    @Test
    void upsert_activeWithoutB0Path_isBlockedByB0Gate() {
        when(definitionRepo.findById("rule.draft")).thenReturn(Optional.of(enabledDefinition("rule.draft")));

        assertThatThrownBy(() -> service.upsertEntry("rule",
                new ModelEnhancementMatrixUpsertRequest("规则", "rule.draft", "  ", "ACTIVE", true, 10)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_010);
    }

    @Test
    void upsert_unknownCapability_isBlockedToEnforceGatewayAccess() {
        when(definitionRepo.findById("rogue.cap")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertEntry("rule",
                new ModelEnhancementMatrixUpsertRequest("规则", "rogue.cap", "B0", "ACTIVE", true, 10)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_001);
    }

    @Test
    void upsert_validActiveWithCapabilityAndB0_persistsActive() {
        when(definitionRepo.findById("rule.draft")).thenReturn(Optional.of(enabledDefinition("rule.draft")));
        when(matrixRepo.findByBusinessPoint("rule")).thenReturn(Optional.empty());

        ModelEnhancementMatrixResponse saved = service.upsertEntry("rule",
            new ModelEnhancementMatrixUpsertRequest("规则", "rule.draft", "确定性规则模板基线", "ACTIVE", true, 10));

        assertThat(saved.accessStatus()).isEqualTo("ACTIVE");
        assertThat(saved.capabilityCode()).isEqualTo("rule.draft");
        assertThat(saved.b0Path()).isEqualTo("确定性规则模板基线");
    }

    @Test
    void upsert_pendingGapWithoutCapabilityOrB0_isAllowedAsHonestGap() {
        when(matrixRepo.findByBusinessPoint("nursing")).thenReturn(Optional.empty());

        ModelEnhancementMatrixResponse saved = service.upsertEntry("nursing",
            new ModelEnhancementMatrixUpsertRequest("护理协同", null, null, "PENDING", true, 90));

        assertThat(saved.accessStatus()).isEqualTo("PENDING");
        assertThat(saved.capabilityCode()).isNull();
    }
}
