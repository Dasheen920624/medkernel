package com.medkernel.engine.authoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ConditionFragmentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    ConditionFragmentService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listEndpointReturnsPagedFragments() throws Exception {
        when(service.list(eq(ConditionFragmentStatus.ACTIVE), eq("pkg-2026.06"), eq("renal"), any(PageRequest.class)))
            .thenReturn(PageResponse.of(List.of(response()), new PageRequest(1, 20, null), 1));

        mvc.perform(get("/api/v1/engine/authoring/fragments")
                .queryParam("status", "ACTIVE")
                .queryParam("packageVersion", "pkg-2026.06")
                .queryParam("keyword", "renal")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].fragmentCode").value("FRAG_RENAL"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void createEndpointReturnsCreatedFragment() throws Exception {
        when(service.create(any(ConditionFragmentUpsertRequest.class))).thenReturn(response());

        mvc.perform(post("/api/v1/engine/authoring/fragments")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fragmentCode": "FRAG_RENAL",
                      "name": "肾功能受限",
                      "category": "肾病",
                      "bodyJson": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
                      "versionNo": 1,
                      "packageVersion": "pkg-2026.06",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fragmentId").value("cf-renal"))
            .andExpect(jsonPath("$.data.bodyJson.all[0].fact").value("patient.age"));
    }

    @Test
    void impactEndpointReturnsAffectedRulesAndPathways() throws Exception {
        when(service.impact("cf-renal")).thenReturn(new ConditionFragmentImpactResponse(
            "cf-renal",
            "FRAG_RENAL",
            1,
            "pkg-2026.06",
            List.of(
                new ConditionFragmentAffectedAsset("RULE", "rule-1", "RULE.RENAL", "肾病规则", "规则当前版本 when 引用条件片段"),
                new ConditionFragmentAffectedAsset("PATHWAY", "path-1", "PATH.RENAL", "肾病路径", "路径守卫引用条件片段")),
            "sha256:abc",
            "trace-fragment"));

        mvc.perform(get("/api/v1/engine/authoring/fragments/cf-renal/impact")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.affectedAssets[0].assetType").value("RULE"))
            .andExpect(jsonPath("$.data.affectedAssets[1].assetType").value("PATHWAY"));
    }

    private ConditionFragmentResponse response() throws Exception {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new ConditionFragmentResponse(
            "cf-renal",
            "tenant-A",
            "FRAG_RENAL",
            "肾功能受限",
            "肾病",
            json.readTree("""
                {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]}
                """),
            1,
            ConditionFragmentStatus.ACTIVE,
            "pkg-2026.06",
            now,
            "author-1",
            now,
            "author-1",
            "trace-fragment");
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("fragment-reader")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("clinical-decision-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("fragment-author")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("medical_affairs")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_GOVERNOR"));
    }
}
