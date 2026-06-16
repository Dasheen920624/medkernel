package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
    void readRoleListsKnowledgeVersionsAsPagedContract() throws Exception {
        when(versionService.listByIdentity(eq(1L), any()))
            .thenReturn(new PageResponse<>(
                List.of(version(10L, 1L, KnowledgeVersionStatus.ACTIVE)),
                1, 20, 1, false, false));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/versions")
                .param("page", "1")
                .param("size", "20")
                .with(jwt()
                    .jwt(token -> token.subject("doctor-1").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(10))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
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

    @Test
    void auditComplianceCanListExportsAsPage() throws Exception {
        when(exportService.listRecent(any(PageRequest.class)))
            .thenReturn(PageResponse.of(List.of(new KnowledgeExportJob(
                1L, "tenant-1", "job-1", "u", ExportType.IDENTITIES, null,
                ExportStatus.PENDING, 0, null, null, null,
                Instant.parse("2026-06-14T00:00:00Z"), null, null, null
            )), new PageRequest(1, 20, null), 21L));

        mvc.perform(get("/api/v1/engine/knowledge/exports?page=1&size=20")
                .with(jwt().jwt(token -> token
                    .subject("u")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("compliance-auditor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].jobCode").value("job-1"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.total").value(21));
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new KnowledgeAssetVersion(
            id, "tenant-A", identityId, "2026", "2026 版",
            null, null, "a".repeat(64), "[]",
            status, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG,
            null, "tenant:tenant-A", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            null, null, null, null, null, null, null, null, null,
            now, "tester", now, "tester", 12, now.plusSeconds(86400));
    }
}
