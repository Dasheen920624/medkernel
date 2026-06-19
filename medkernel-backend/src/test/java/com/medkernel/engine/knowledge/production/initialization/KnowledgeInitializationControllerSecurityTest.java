package com.medkernel.engine.knowledge.production.initialization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.medkernel.shared.context.RequestContext;

/** 知识初始化发行 API 权限测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KnowledgeInitializationControllerSecurityTest {

    private static final String APPROVAL_BODY = "{\"reason\":\"已核对官方版本与许可\"}";
    private static final String LOW_APPROVAL_BODY = """
        {"expectedOverallHash":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
         "idempotencyKey":"bulk-low-1","reason":"批准低风险候选"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SourceVersionApprovalService sourceApprovalService;

    @MockBean
    private KnowledgeInitializationService initializationService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadInitializationCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/initialization/catalog"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanReadCatalogAndBatches() throws Exception {
        when(initializationService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine/knowledge-production/initialization/catalog")
                .with(knowledgeGovernor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].catalogCode").value("KNOWGEN-29"));
        mockMvc.perform(get("/api/v1/engine/knowledge-production/initialization/batches")
                .with(knowledgeGovernor()))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeGovernorCannotApproveSourceVersion() throws Exception {
        mockMvc.perform(post(
                "/api/v1/engine/knowledge-production/initialization/source-versions/9/approval")
                .with(knowledgeGovernor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(APPROVAL_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void systemSuperadminCanApproveSourceVersion() throws Exception {
        when(sourceApprovalService.approve(anyLong(), any())).thenReturn(null);

        mockMvc.perform(post(
                "/api/v1/engine/knowledge-production/initialization/source-versions/9/approval")
                .with(systemSuperadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(APPROVAL_BODY))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeGovernorCanApproveLowAndRefreshBatch() throws Exception {
        when(initializationService.approveLow(anyString(), any())).thenReturn(null);
        when(initializationService.refresh(anyString())).thenReturn(null);

        mockMvc.perform(post(
                "/api/v1/engine/knowledge-production/initialization/batches/foundation-f1/approve-low")
                .with(knowledgeGovernor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOW_APPROVAL_BODY))
            .andExpect(status().isOk());
        mockMvc.perform(post(
                "/api/v1/engine/knowledge-production/initialization/batches/foundation-f1/refresh")
                .with(knowledgeGovernor()))
            .andExpect(status().isOk());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .JwtRequestPostProcessor knowledgeGovernor() {
        return jwt().jwt(token -> token.subject("reviewer").claim("tenant_id", "tenant-1")
                .claim("roles", List.of("knowledge-governor")))
            .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .JwtRequestPostProcessor systemSuperadmin() {
        return jwt().jwt(token -> token.subject("admin").claim("tenant_id", "tenant-1")
                .claim("roles", List.of("system-superadmin")))
            .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN"));
    }
}
