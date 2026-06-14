package com.medkernel.engine.datasvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;

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

/**
 * 引擎数据服务层控制器权限安全测试（DATASVC-01 PR1，{@code engine-data.read}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EngineDataControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleUsageStatsService service;

    @MockBean
    private KnowledgeUsageStatsService knowledgeUsageStatsService;

    @MockBean
    private ClinicalSignalsService clinicalSignalsService;

    @MockBean
    private ControlledToolService controlledToolService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void clinicalUserCannotQueryRuleUsage() throws Exception {
        mockMvc.perform(get("/api/v1/engine-data/rule-usage")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanQueryRuleUsage() throws Exception {
        when(service.queryRuleUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(new RuleUsageStatsResponse(EngineDataLevel.D2, 0L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        mockMvc.perform(get("/api/v1/engine-data/rule-usage")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotQueryKnowledgeUsage() throws Exception {
        mockMvc.perform(get("/api/v1/engine-data/knowledge-usage")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanQueryKnowledgeUsage() throws Exception {
        when(knowledgeUsageStatsService.queryKnowledgeUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(new KnowledgeUsageStatsResponse(EngineDataLevel.D2, 0L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        mockMvc.perform(get("/api/v1/engine-data/knowledge-usage")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotQueryClinicalSignals() throws Exception {
        mockMvc.perform(get("/api/v1/engine-data/clinical-signals")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanQueryClinicalSignals() throws Exception {
        when(clinicalSignalsService.queryClinicalSignals(any(), any(), anyInt(), anyInt()))
            .thenReturn(new ClinicalSignalsResponse(EngineDataLevel.D2, 0L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        mockMvc.perform(get("/api/v1/engine-data/clinical-signals")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotExecuteControlledTool() throws Exception {
        mockMvc.perform(post("/api/v1/engine-data/tools/queryRuleUsage:execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"探测\"}")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanListControlledTools() throws Exception {
        when(controlledToolService.listTools()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine-data/tools")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk());
    }
}
