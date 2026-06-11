package com.medkernel.engine.knowledge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证知识资产 Controller 的权限矩阵 + @DataScope。
 *
 * <p>关键断言：
 * <ul>
 *   <li>knowledge.read：DOCTOR / NURSE / SPECIALIST / MEDICAL_AFFAIRS / AUDIT_COMPLIANCE 通过；GUEST 403</li>
 *   <li>knowledge.publish：MEDICAL_AFFAIRS / HOSPITAL_ADMIN 通过；DOCTOR / NURSE 403</li>
 *   <li>knowledge.withdraw：同 publish</li>
 *   <li>所有角色都必须有租户上下文，否则 400 ENG-BASE-001</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KnowledgeIdentityControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    KnowledgeIdentityService identityService;

    @MockBean
    KnowledgeVersionService versionService;

    @MockBean
    KnowledgeExportService exportService;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    // ─── knowledge.read 权限矩阵 ─────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCanReadButDataScopeRejectsMissingTenant() throws Exception {
        when(identityService.page(any(), any())).thenReturn(null);
        mvc.perform(get("/api/v1/engine/knowledge/identities"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestRoleIsForbiddenFromIdentitiesList() throws Exception {
        mvc.perform(get("/api/v1/engine/knowledge/identities"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void auditComplianceCanReadKnowledge() throws Exception {
        mvc.perform(get("/api/v1/engine/knowledge/identities"))
            .andExpect(status().isBadRequest()) // tenant 缺失 → 但权限验证已过
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCanReachProvenanceButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/knowledge/identities/1/provenance"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestRoleIsForbiddenFromProvenance() throws Exception {
        mvc.perform(get("/api/v1/engine/knowledge/identities/1/provenance"))
            .andExpect(status().isForbidden());
    }

    // ─── knowledge.publish（activate）─────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCannotActivate() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions/10/activate"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KNOWLEDGE_GOVERNOR")
    void knowledgeGovernorCanReachActivateButDataScopeFails() throws Exception {
        when(versionService.activate(eq(1L), eq(10L), any(), any()))
            .thenReturn(null);
        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions/10/activate"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    // ─── knowledge.withdraw ──────────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_NURSING_COLLABORATOR")
    void nurseCannotWithdraw() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions/10/withdraw")
                .contentType("application/json")
                .content("{\"reason\":\"上游召回\"}"))
            .andExpect(status().isForbidden());
    }

    // ─── knowledge.export ───────────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCannotSubmitExport() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/exports")
                .contentType("application/json")
                .content("{\"type\":\"IDENTITIES\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void auditComplianceCanSubmitExportButDataScopeFails() throws Exception {
        when(exportService.submit(any(), any())).thenReturn(null);
        mvc.perform(post("/api/v1/engine/knowledge/exports")
                .contentType("application/json")
                .content("{\"type\":\"IDENTITIES\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
