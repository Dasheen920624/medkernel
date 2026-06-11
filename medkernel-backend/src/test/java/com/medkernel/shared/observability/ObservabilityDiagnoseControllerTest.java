package com.medkernel.shared.observability;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse.PayloadSummary;
import com.medkernel.shared.observability.DiagnoseResponse.StateTransitionEntry;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ObservabilityDiagnoseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObservabilityDiagnoseService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void traceDiagnosisRequiresSystemOrAuditReadPermission() throws Exception {
        Instant now = Instant.parse("2026-06-01T08:00:00Z");
        when(service.findByTraceId("trace-x")).thenReturn(new TraceDiagnoseResponse(
            "trace-x",
            now,
            now.plusMillis(42),
            42L,
            List.of(new StateTransitionEntry(
                null, "PROCESSED", "DONE", "ops-1", "trace-x", null, now)),
            List.of(new PayloadSummary(
                "sha256-a", 128L, "application/json",
                PayloadRef.STORAGE_INLINE, "db://mk_obs_payload_store/pl-1"))
        ));

        mockMvc.perform(get("/api/v1/engine/diagnose/traces/trace-x")
                .with(jwt().jwt(token -> token
                    .subject("ops-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.traceId").value("trace-x"))
            .andExpect(jsonPath("$.data.durationMs").value(42))
            .andExpect(jsonPath("$.data.payloads[0].contentType").value("application/json"))
            .andExpect(jsonPath("$.data.payloads[0].fetchUri").value("db://mk_obs_payload_store/pl-1"));

        verify(service).findByTraceId("trace-x");
    }

    @Test
    void doctorWithoutAuditOrSystemReadIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/engine/diagnose/traces/trace-x")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isForbidden());
    }
}
