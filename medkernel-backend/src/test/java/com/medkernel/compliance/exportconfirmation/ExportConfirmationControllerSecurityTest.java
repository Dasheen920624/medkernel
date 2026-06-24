package com.medkernel.compliance.exportconfirmation;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ExportConfirmationControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ExportConfirmationService service;

    @Test
    void exportOperatorWithTenantCanConfirmScope() throws Exception {
        when(service.confirmExport(
            eq("t-1"),
            any(ExportConfirmationRequest.class),
            eq("export-auditor")
        )).thenReturn(response(ExportConfirmationStatus.CONFIRMED));

        mvc.perform(post("/api/v1/compliance/exports:confirm")
                .with(exportJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.confirmedBy").value("export-auditor"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void clinicalUserWithoutExportPermissionCannotConfirmScope() throws Exception {
        mvc.perform(post("/api/v1/compliance/exports:confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void oldRequestAndApproveEndpointsDoNotExist() throws Exception {
        mvc.perform(post("/api/v1/compliance/exports:request")
                .with(exportJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/compliance/exports/exp-audit-1:approve")
                .with(exportJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor exportJwt() {
        return jwt().jwt(token -> token
            .subject("export-auditor")
            .claim("tenant_id", "t-1")
            .claim("group_id", "g-1")
            .claim("hospital_id", "h-1")
            .claim("department_id", "compliance")
            .claim("roles", List.of("auditor")))
            .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"));
    }

    private String confirmBody() {
        return """
            {
              "resourceType": "AUDIT_EVENT",
              "exportScope": {
                "resourceType": "AUDIT_EVENT",
                "filters": {},
                "selectedScope": "FILTERED_RESULT"
              },
              "reason": "复核当前审计范围",
              "idempotencyKey": "idem-001"
            }
            """;
    }

    private ExportConfirmationResponse response(ExportConfirmationStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new ExportConfirmationResponse(
            "exp-audit-event-idem-001",
            "audit_event",
            "{\"resourceType\":\"AUDIT_EVENT\",\"filters\":{},"
                + "\"selectedScope\":\"FILTERED_RESULT\"}",
            "idem-001",
            "复核当前审计范围",
            status,
            "export-auditor",
            "evd-exp-audit-event-idem-001-confirmation",
            "/api/v1/compliance/evidence/snapshots/confirmation/file",
            null,
            null,
            null,
            null,
            1L,
            now
        );
    }
}
