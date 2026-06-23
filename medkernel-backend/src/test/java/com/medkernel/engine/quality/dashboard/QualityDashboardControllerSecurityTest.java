package com.medkernel.engine.quality.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.quality.value.ValueMetricSummaryResponse;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class QualityDashboardControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean QualityDashboardService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedCannotReadQualityDashboard() throws Exception {
        mvc.perform(get("/api/v1/engine/quality/dashboard"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void qaManagerCanReadDashboardDrilldownAndAlerts() throws Exception {
        when(service.dashboard(any())).thenReturn(new QualityDashboardResponse(
            new QualityDashboardSummary(0, 0, 0, 0, 0, 0),
            List.of(), new ValueMetricSummaryResponse(List.of()), List.of(), Instant.EPOCH));
        when(service.drilldown(any(), eq(QualityDashboardDrilldownType.FINDING), eq(0), eq(20)))
            .thenReturn(new QualityDashboardDrilldownResponse(
                QualityDashboardDrilldownType.FINDING, List.of(),
                new QualityEvidencePackage("empty", Instant.EPOCH, List.of()), 0, 20, 0, false));
        when(service.alerts(any(), eq(0), eq(20)))
            .thenReturn(new QualityDashboardAlertsResponse(List.of(), 0, 20, 0, false));

        mvc.perform(get("/api/v1/engine/quality/dashboard")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/engine/quality/dashboard/drilldown")
                .param("type", "FINDING")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/engine/quality/alerts")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void qaManagerCanAcknowledgeQualityAlert() throws Exception {
        when(service.acknowledgeAlert("alert-1")).thenReturn(new QualityDashboardAlertResponse(
            "alert-1", QualityDashboardAlertType.HIGH_RISK_FINDING, QualityDashboardAlertStatus.ACKNOWLEDGED,
            "dept-a", "quality_finding", "qf-1", "P1", "OPEN_P0_P1_FINDING",
            null, null, "高风险质控问题待闭环", "证据", Instant.EPOCH, Instant.EPOCH, "trace-1"));

        mvc.perform(post("/api/v1/engine/quality/alerts/alert-1/acknowledge")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void doctorCannotReadQualityDashboard() throws Exception {
        mvc.perform(get("/api/v1/engine/quality/dashboard")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isForbidden());
    }
}
