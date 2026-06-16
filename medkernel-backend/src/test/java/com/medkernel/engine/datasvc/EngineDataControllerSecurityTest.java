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

import com.medkernel.engine.datasvc.export.EngineDataExportJob;
import com.medkernel.engine.datasvc.export.EngineDataExportService;
import com.medkernel.engine.datasvc.export.EngineDataExportType;
import com.medkernel.engine.datasvc.export.ExportJobStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层控制器权限安全测试（DATASVC-01，{@code engine-data.read} 读侧 + {@code engine-data.export} 导出侧）。
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

    @MockBean
    private EngineDataExportService engineDataExportService;

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

    @Test
    void clinicalUserCannotSubmitExport() throws Exception {
        mockMvc.perform(post("/api/v1/engine-data/exports")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exportType\":\"RULE_USAGE\",\"windowDays\":90,\"approvalId\":\"exp-1\",\"idempotencyKey\":\"idem-1\"}")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanSubmitExport() throws Exception {
        when(engineDataExportService.submit(any(), anyInt(), any(), any()))
            .thenReturn(new EngineDataExportJob(1L, "tenant-1", "job-1", "u",
                EngineDataExportType.RULE_USAGE, ExportJobStatus.PENDING, 0, null, null, null,
                "exp-1", "idem-1", "{}", Instant.parse("2026-06-14T00:00:00Z"), null, null, null));

        mockMvc.perform(post("/api/v1/engine-data/exports")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exportType\":\"RULE_USAGE\",\"windowDays\":90,\"approvalId\":\"exp-1\",\"idempotencyKey\":\"idem-1\"}")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void qualityGovernorCanListExportsAsPage() throws Exception {
        when(engineDataExportService.listRecent(any(PageRequest.class)))
            .thenReturn(PageResponse.of(List.of(new EngineDataExportJob(1L, "tenant-1", "job-1", "u",
                EngineDataExportType.RULE_USAGE, ExportJobStatus.PENDING, 0, null, null, null,
                "exp-1", "idem-1", "{}", Instant.parse("2026-06-14T00:00:00Z"), null, null, null)),
                new PageRequest(1, 20, null), 21L));

        mockMvc.perform(get("/api/v1/engine-data/exports?page=1&size=20")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items[0].jobCode").value("job-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.page").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.total").value(21));
    }
}
