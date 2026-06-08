package com.medkernel.engine.authoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthoringPreviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthoringPreviewService service;

    @MockBean
    AuthoringPreviewRunService previewRunService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void previewEndpointReturnsReadableConditionText() throws Exception {
        when(service.preview(any(AuthoringPreviewRequest.class)))
            .thenReturn(new AuthoringPreviewResponse(
                "当 年龄 大于等于 65，则 阻断高危用药。",
                List.of("当 年龄 大于等于 65"),
                List.of(),
                List.of(),
                "trace-preview"));

        mvc.perform(post("/api/v1/engine/authoring/preview")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-preview",
                      "trace_id": "trace-preview",
                      "tenant_id": "t-1",
                      "user_id": "api-author",
                      "role_codes": ["doctor"],
                      "package_version": "pkg-2026.1",
                      "subject": "RULE_CONDITION",
                      "dsl": {
                        "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
                        "then": [{"actionCode": "BLOCK", "summary": "阻断高危用药"}]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.previewText").value("当 年龄 大于等于 65，则 阻断高危用药。"));
    }

    @Test
    void previewEndpointRequiresUnifiedContextFields() throws Exception {
        mvc.perform(post("/api/v1/engine/authoring/preview")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "subject": "PATHWAY_GUARD",
                      "dsl": {"guard": {"fact": "risk.level", "operator": "equals", "value": "HIGH"}}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    @Test
    void previewRunEndpointReturnsDraftExecutionEvidence() throws Exception {
        when(previewRunService.run(any(AuthoringPreviewRunRequest.class)))
            .thenReturn(new AuthoringPreviewRunResponse(
                AuthoringPreviewSubject.RULE_CONDITION,
                "ctx-001",
                "pkg-2026.1",
                true,
                true,
                "草稿规则命中真实快照",
                "CRITICAL",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                java.util.Map.of(),
                java.util.Map.of("observations", 1),
                List.of(),
                null,
                null,
                "trace-preview-run"));

        mvc.perform(post("/api/v1/engine/authoring/preview-run")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-preview-run",
                      "trace_id": "trace-preview-run",
                      "tenant_id": "t-1",
                      "user_id": "api-author",
                      "role_codes": ["doctor"],
                      "package_version": "pkg-2026.1",
                      "subject": "RULE_CONDITION",
                      "snapshot_id": "ctx-001",
                      "dsl": {
                        "trigger": "result-review",
                        "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
                        "then": []
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.snapshotId").value("ctx-001"))
            .andExpect(jsonPath("$.data.outcomeText").value("草稿规则命中真实快照"))
            .andExpect(jsonPath("$.data.contextResourceCounts.observations").value(1));
    }

    @Test
    void previewEndpointRejectsMissingReadPermission() throws Exception {
        mvc.perform(post("/api/v1/engine/authoring/preview")
                .with(jwt().jwt(token -> token
                        .subject("api-guest")
                        .claim("tenant_id", "t-1")
                        .claim("roles", List.of("guest")))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-preview",
                      "trace_id": "trace-preview",
                      "tenant_id": "t-1",
                      "user_id": "api-guest",
                      "role_codes": ["guest"],
                      "package_version": "pkg-2026.1",
                      "subject": "RULE_CONDITION",
                      "dsl": {"when": {"fact": "patient.age", "operator": "gte", "value": 65}}
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api-author")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("doctor")))
            .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
    }
}
