package com.medkernel.engine.knowledge.acquisition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 公域知识获取控制器权限测试（AIK-STD-14）。
 *
 * <p>触发获取会写受控来源解析链路，走 {@code knowledge.write}；来源允许清单和运行台账只读查询走
 * {@code knowledge.read}。临床用户和 GUEST 不得触发外网获取。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AcquisitionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcquisitionOrchestrationService service;

    @MockBean
    private AcquisitionSourceGovernanceService sourceGovernanceService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = """
        {"sourceCode":"NHC-HTN","url":"https://guideline.example.org/htn.txt",
         "versionNo":"v2026","format":"STRUCTURED_TEXT"}
        """;

    private static final String SOURCE_DRAFT_BODY = """
        {
          "domain": "www.nhc.gov.cn",
          "baseUrl": "https://www.nhc.gov.cn/",
          "sourceType": "GUIDELINE",
          "authorityLevel": "B_GUIDELINE",
          "authorityBasis": "国家级官方来源",
          "title": "公开指南",
          "publisher": "国家卫生健康委",
          "license": "公开访问，仅内部留存原件用于锚点和审计",
          "licensePolicy": "PERMITTED",
          "robotsPolicy": "ALLOW_FETCH",
          "scheduleEnabled": false
        }
        """;

    private static KnowledgeAcquisitionRunResponse runResponse() {
        return new KnowledgeAcquisitionRunResponse(
            "acq:x",
            KnowledgeAcquisitionRunStatus.SUCCEEDED,
            "NHC-HTN",
            "https://guideline.example.org/htn.txt",
            "guideline.example.org",
            "a".repeat(64),
            128L,
            "text/plain; charset=UTF-8",
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/htn.txt",
            7L,
            9L,
            "dpj:x",
            null,
            null);
    }

    private static KnowledgeAcquisitionSource source() {
        return new KnowledgeAcquisitionSource(
            11L,
            "tenant-1",
            "NHC-HTN",
            "guideline.example.org",
            "https://guideline.example.org",
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家卫生健康委公开指南",
            "高血压诊疗指南",
            "国家卫生健康委",
            "公开资料许可",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            "Y",
            "N",
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            "super-admin",
            Instant.EPOCH,
            "super-admin",
            0L);
    }

    private static KnowledgeAcquisitionRun run() {
        return new KnowledgeAcquisitionRun(
            5L,
            "tenant-1",
            "acq:x",
            11L,
            "NHC-HTN",
            "https://guideline.example.org/htn.txt",
            "guideline.example.org",
            AcquisitionTriggerType.MANUAL,
            KnowledgeAcquisitionRunStatus.SUCCEEDED,
            Instant.EPOCH,
            "a".repeat(64),
            128L,
            "text/plain; charset=UTF-8",
            "公开资料许可",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/htn.txt",
            7L,
            9L,
            "dpj:x",
            null,
            Instant.EPOCH,
            "user-001",
            Instant.EPOCH,
            "user-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void clinicalUserCannotTriggerAcquisitionRun() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/runs")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotTriggerAcquisitionRun() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/runs")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanTriggerAcquisitionRun() throws Exception {
        when(service.run(any())).thenReturn(runResponse());

        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/runs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.materialFileUri").value(
                "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/htn.txt"));
    }

    @Test
    void acquisitionRunRejectsBlankUrlWith400() throws Exception {
        String invalid = "{\"sourceCode\":\"NHC-HTN\",\"url\":\"\",\"versionNo\":\"v2026\",\"format\":\"STRUCTURED_TEXT\"}";
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/runs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotListAcquisitionSources() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge/acquisition/sources"))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanListAcquisitionSources() throws Exception {
        when(service.listSources(anyInt(), anyInt()))
            .thenReturn(PageResponse.of(List.of(source()), new PageRequest(1, 20, null), 1L));

        mockMvc.perform(get("/api/v1/engine/knowledge/acquisition/sources")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].sourceCode").value("NHC-HTN"))
            .andExpect(jsonPath("$.data.items[0].domain").value("guideline.example.org"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotListAcquisitionRuns() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge/acquisition/runs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanListAcquisitionRuns() throws Exception {
        when(service.listRuns(anyInt(), anyInt()))
            .thenReturn(PageResponse.of(List.of(run()), new PageRequest(1, 20, null), 1L));

        mockMvc.perform(get("/api/v1/engine/knowledge/acquisition/runs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].runCode").value("acq:x"))
            .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"));
    }

    @Test
    void engineOperatorCanSaveSourceDraft() throws Exception {
        mockMvc.perform(put("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(SOURCE_DRAFT_BODY))
            .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotSaveSourceDraft() throws Exception {
        mockMvc.perform(put("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(SOURCE_DRAFT_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicalUserCannotEnableOrDisableSource() throws Exception {
        var authentication = jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));

        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/enable")
                .with(authentication))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/disable")
                .with(authentication))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanEnableAndDisableSource() throws Exception {
        var authentication = jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));

        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/enable")
                .with(authentication))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/disable")
                .with(authentication))
            .andExpect(status().isOk());
    }

    @Test
    void systemSuperadminCanEnableAndDisableSource() throws Exception {
        var authentication = jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                .claim("roles", List.of("system-superadmin")))
            .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN"));

        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/enable")
                .with(authentication))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/engine/knowledge/acquisition/sources/NHC-GUIDELINE/disable")
                .with(authentication))
            .andExpect(status().isOk());
    }
}
