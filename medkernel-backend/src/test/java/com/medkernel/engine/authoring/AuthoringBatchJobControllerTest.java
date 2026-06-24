package com.medkernel.engine.authoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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
class AuthoringBatchJobControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthoringBatchJobService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void generateEndpointReturnsBatchJob() throws Exception {
        when(service.generateRules(any(AuthoringBatchRuleGenerateRequest.class)))
            .thenReturn(job(AuthoringBatchJobType.RULE_GENERATE, AuthoringBatchJobStatus.PARTIAL_SUCCESS));

        mvc.perform(post("/api/v1/engine/authoring/batch/rules/generate")
                .with(authorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "templateRuleId": "rule-template",
                      "rows": [
                        {
                          "rowId": "row-1",
                          "ruleCode": "RULE.CKD.1",
                          "name": "CKD 阈值 1",
                          "parameterBindings": {"threshold": 45}
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobType").value("RULE_GENERATE"))
            .andExpect(jsonPath("$.data.status").value("PARTIAL_SUCCESS"));
    }

    @Test
    void legacyPackageDistributionEndpointIsRetired() throws Exception {
        mvc.perform(post("/api/v1/engine/authoring/batch/packages/distribute")
                .with(authorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {
                          "itemId": "row-1",
                          "packageId": "package-1",
                          "targetOrgUnitId": "hospital-offline",
                          "strategy": "FULL",
                          "scopeType": "FACILITY",
                          "scopeValue": "hospital-offline",
                          "adapterIds": ["fhir"],
                          "reason": "批量分发"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void recentEndpointRejectsMissingReadPermission() throws Exception {
        mvc.perform(get("/api/v1/engine/authoring/batch")
                .with(jwt().jwt(token -> token
                        .subject("guest")
                        .claim("tenant_id", "tenant-A")
                        .claim("roles", List.of("guest")))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recentEndpointReturnsServerPage() throws Exception {
        when(service.listRecent(any(PageRequest.class)))
            .thenReturn(PageResponse.of(
                List.of(job(AuthoringBatchJobType.RULE_GENERATE, AuthoringBatchJobStatus.SUCCEEDED)),
                new PageRequest(2, 20, null),
                41L));

        mvc.perform(get("/api/v1/engine/authoring/batch?page=2&size=20")
                .with(authorJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].jobId").value("abj-1"))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.total").value(41));
    }

    private AuthoringBatchJobResponse job(AuthoringBatchJobType type, AuthoringBatchJobStatus status) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new AuthoringBatchJobResponse(
            "abj-1",
            type,
            status,
            1,
            1,
            0,
            "{}",
            List.of(new AuthoringBatchItemResponse(
                "row-1",
                AuthoringBatchItemStatus.SUCCEEDED,
                "RULE",
                "rule-1",
                "{}",
                null,
                null,
                "已处理",
                now)),
            "trace-batch",
            now,
            now);
    }

    private static RequestPostProcessor authorJwt() {
        return jwt().jwt(token -> token
                .subject("batch-author")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }

}
