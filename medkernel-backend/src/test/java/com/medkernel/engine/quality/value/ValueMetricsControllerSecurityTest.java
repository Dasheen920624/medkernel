package com.medkernel.engine.quality.value;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
class ValueMetricsControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean ValueMetricsService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedCannotReadValueMetrics() throws Exception {
        mvc.perform(get("/api/v1/engine/value-metrics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void qaManagerCanReadValueMetricsAndDrilldown() throws Exception {
        when(service.summary(any())).thenReturn(new ValueMetricSummaryResponse(List.of()));
        when(service.drilldown(eq(ValueMetricCode.MISSED_CASE_RETROSPECTIVE), any(), eq(0), eq(20)))
            .thenReturn(new ValueMetricDrilldownResponse(null, List.of(), 0, 20, 0, false));

        mvc.perform(get("/api/v1/engine/value-metrics")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/engine/value-metrics/MISSED_CASE_RETROSPECTIVE/drilldown")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
            .andExpect(status().isOk());
    }

    @Test
    void doctorCannotReadValueMetrics() throws Exception {
        mvc.perform(get("/api/v1/engine/value-metrics")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isForbidden());
    }
}
