package com.medkernel.compliance.exportconfirmation;

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

class ExportConfirmationGateServiceTest {

    private static final String SCOPE = "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}";

    private ExportConfirmationRepository repository;
    private ExportConfirmationGateService gate;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ExportConfirmationRepository.class);
        gate = new ExportConfirmationGateService(repository, new ObjectMapper());
    }

    @Test
    void confirmedMatchingScopePasses() {
        when(repository.findByTenantIdAndConfirmationId("t-1", "exp-1"))
            .thenReturn(Optional.of(confirmation("CONFIRMED", "engine_data_rule_usage", SCOPE)));

        assertThatCode(() -> gate.requireConfirmedForExport(
            "t-1",
            "exp-1",
            "engine_data_rule_usage",
            "{\"windowDays\":90,\"exportType\":\"RULE_USAGE\"}"
        )).doesNotThrowAnyException();
    }

    @Test
    void missingConfirmationIsForbidden() {
        when(repository.findByTenantIdAndConfirmationId("t-1", "exp-x"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.requireConfirmedForExport(
            "t-1",
            "exp-x",
            "engine_data_rule_usage",
            SCOPE
        )).isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void resourceOrScopeMismatchIsConflict() {
        when(repository.findByTenantIdAndConfirmationId("t-1", "exp-2"))
            .thenReturn(Optional.of(confirmation("CONFIRMED", "audit_event", SCOPE)));
        when(repository.findByTenantIdAndConfirmationId("t-1", "exp-3"))
            .thenReturn(Optional.of(confirmation(
                "CONFIRMED",
                "engine_data_rule_usage",
                "{\"exportType\":\"RULE_USAGE\",\"windowDays\":30}"
            )));

        assertThatThrownBy(() -> gate.requireConfirmedForExport(
            "t-1",
            "exp-2",
            "engine_data_rule_usage",
            SCOPE
        )).isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> gate.requireConfirmedForExport(
            "t-1",
            "exp-3",
            "engine_data_rule_usage",
            SCOPE
        )).isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private ExportConfirmation confirmation(
            String status,
            String resourceType,
            String scopeSnapshot) {
        Instant now = Instant.now();
        return new ExportConfirmation(
            1L,
            "exp-1",
            "t-1",
            resourceType,
            scopeSnapshot,
            "idem-1",
            "导出统计供质控分析",
            "auditor-1",
            now,
            status,
            null,
            null,
            "evd-confirmation",
            "/evidence/confirmation",
            null,
            null,
            1L,
            now,
            "auditor-1",
            now,
            "auditor-1",
            "trace"
        );
    }
}
