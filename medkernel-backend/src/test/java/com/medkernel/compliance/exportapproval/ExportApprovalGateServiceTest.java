package com.medkernel.compliance.exportapproval;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 导出审批闸服务测试：校验「不绕审批」——须有 APPROVED 审批且资源类型 + 范围一致。
 */
class ExportApprovalGateServiceTest {

    private static final String SCOPE = "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}";

    private ExportApprovalRepository repository;
    private ExportApprovalGateService gate;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ExportApprovalRepository.class);
        gate = new ExportApprovalGateService(repository, new ObjectMapper());
    }

    @Test
    void approvedMatchingScopePasses() {
        when(repository.findByTenantIdAndApprovalId("t-1", "exp-1"))
            .thenReturn(Optional.of(approval("APPROVED", "engine_data_rule_usage", SCOPE)));

        // 范围键序颠倒但 JSON 等价，应通过。
        assertThatCode(() -> gate.requireApprovedForExport(
            "t-1", "exp-1", "engine_data_rule_usage", "{\"windowDays\":90,\"exportType\":\"RULE_USAGE\"}"))
            .doesNotThrowAnyException();
    }

    @Test
    void missingApprovalIsForbidden() {
        when(repository.findByTenantIdAndApprovalId("t-1", "exp-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.requireApprovedForExport("t-1", "exp-x", "engine_data_rule_usage", SCOPE))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void notApprovedIsForbidden() {
        when(repository.findByTenantIdAndApprovalId("t-1", "exp-2"))
            .thenReturn(Optional.of(approval("REQUESTED", "engine_data_rule_usage", SCOPE)));

        assertThatThrownBy(() -> gate.requireApprovedForExport("t-1", "exp-2", "engine_data_rule_usage", SCOPE))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void resourceTypeMismatchIsConflict() {
        when(repository.findByTenantIdAndApprovalId("t-1", "exp-3"))
            .thenReturn(Optional.of(approval("APPROVED", "audit_event", SCOPE)));

        assertThatThrownBy(() -> gate.requireApprovedForExport("t-1", "exp-3", "engine_data_rule_usage", SCOPE))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void scopeMismatchIsConflict() {
        when(repository.findByTenantIdAndApprovalId("t-1", "exp-4"))
            .thenReturn(Optional.of(approval("APPROVED", "engine_data_rule_usage",
                "{\"exportType\":\"RULE_USAGE\",\"windowDays\":30}")));

        assertThatThrownBy(() -> gate.requireApprovedForExport("t-1", "exp-4", "engine_data_rule_usage", SCOPE))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    private ExportApproval approval(String status, String resourceType, String scopeSnapshot) {
        Instant now = Instant.now();
        return new ExportApproval(1L, "exp-1", "t-1", resourceType, scopeSnapshot, "idem-1",
            "导出统计供质控分析", "quality-1", now, status,
            null, null, null, null, null, null, null, null, null, null,
            1L, now, "quality-1", now, "quality-1", "trace");
    }
}
