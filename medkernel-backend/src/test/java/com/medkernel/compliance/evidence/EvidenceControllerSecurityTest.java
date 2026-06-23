package com.medkernel.compliance.evidence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.context.RequestContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 证据链控制器安全矩阵测试（GA-ENG-EVID-01）。
 *
 * <p>验证 Controller 层的权限校验与 DataScope 强多租户隔离拦截，
 * 不验证业务逻辑（业务由 {@link EvidenceServiceTest} 覆盖）。
 *
 * <p>测试矩阵：
 * <ul>
 *   <li>有 {@code audit.read} 权限但缺租户上下文 → 400（ENG-BASE-001）</li>
 *   <li>无 {@code audit.read} 权限 → 403</li>
 *   <li>有 {@code audit.export} 权限但缺租户上下文 → 400（ENG-BASE-001）</li>
 *   <li>无 {@code audit.export} 权限 → 403</li>
 *   <li>匿名访问 → 401</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EvidenceControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    EvidenceService evidenceService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    // ── GET /snapshots（列表检索）──────────────────────────────

    @Test
    @DisplayName("审计合规角色可到达列表端点，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void listSnapshots_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("普通医生角色无 audit.read 权限，直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void listSnapshots_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("访客角色无 audit.read 权限，直接 403")
    @WithMockUser(authorities = "ROLE_GUEST")
    void listSnapshots_guestRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots"))
            .andExpect(status().isForbidden());
    }

    // ── GET /snapshots/{evidenceId}（详情查询）─────────────────

    @Test
    @DisplayName("审计角色查询详情，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void getSnapshot_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/evd-test-001"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色查询详情直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void getSnapshot_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/evd-test-001"))
            .andExpect(status().isForbidden());
    }

    // ── GET /snapshots/{evidenceId}/file（真实证据文件下载）───────

    @Test
    @DisplayName("审计角色下载证据文件，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void downloadSnapshotFile_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/evd-test-001/file"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色下载证据文件直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void downloadSnapshotFile_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/evd-test-001/file"))
            .andExpect(status().isForbidden());
    }

    // ── POST /snapshots（创建存证）─────────────────────────────

    @Test
    @DisplayName("审计角色创建存证，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void createSnapshot_auditRole_noTenant_returns400() throws Exception {
        String body = """
            {
                "evidenceId": "evd-test-001",
                "evidenceType": "KNOWLEDGE_SOURCE",
                "action": "CREATE",
                "subjectType": "guideline",
                "subjectId": "guideline-stroke-v3",
                "evidenceSummary": "测试证据",
                "payloadSnapshot": "{\\"key\\":\\"value\\"}"
            }
            """;
        mvc.perform(post("/api/v1/compliance/evidence/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色无 audit.export 权限，创建存证直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void createSnapshot_doctorRole_returns403() throws Exception {
        String body = """
            {
                "evidenceId": "evd-test-001",
                "evidenceType": "KNOWLEDGE_SOURCE",
                "action": "CREATE",
                "subjectType": "guideline",
                "subjectId": "guideline-stroke-v3",
                "evidenceSummary": "测试证据",
                "payloadSnapshot": "{\\"key\\":\\"value\\"}"
            }
            """;
        mvc.perform(post("/api/v1/compliance/evidence/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    // ── POST /snapshots/{id}/verify（验签）─────────────────────

    @Test
    @DisplayName("审计角色验签请求，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void verifySnapshot_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(post("/api/v1/compliance/evidence/snapshots/evd-test-001/verify"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色验签直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void verifySnapshot_doctorRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/compliance/evidence/snapshots/evd-test-001/verify"))
            .andExpect(status().isForbidden());
    }

    // ── POST /snapshots/export（导出）──────────────────────────

    @Test
    @DisplayName("审计角色导出请求，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void exportSnapshots_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(post("/api/v1/compliance/evidence/snapshots/export"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色无 audit.export 权限，导出直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void exportSnapshots_doctorRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/compliance/evidence/snapshots/export"))
            .andExpect(status().isForbidden());
    }

    // ── GET /snapshots/export/{digest}/download（证据包下载）─────

    @Test
    @DisplayName("审计角色下载证据包，缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void downloadExportFile_auditRole_noTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/export/"
                + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/download"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("医生角色下载证据包直接 403")
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void downloadExportFile_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots/export/"
                + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/download"))
            .andExpect(status().isForbidden());
    }

    // ── 匿名访问 ────────────────────────────────────────────

    @Test
    @DisplayName("未认证匿名请求，返回 401")
    void anonymous_returns401() throws Exception {
        mvc.perform(get("/api/v1/compliance/evidence/snapshots"))
            .andExpect(status().isUnauthorized());
    }
}
