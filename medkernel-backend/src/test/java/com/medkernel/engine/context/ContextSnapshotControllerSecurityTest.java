package com.medkernel.engine.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * 端到端验证标准上下文 Controller 的权限矩阵 + @DataScope。
 *
 * <p>关键断言：
 * <ul>
 *   <li>未授权访问 → 403</li>
 *   <li>有权限但缺租户 → 400 ENG-BASE-001（DataScopeAspect 兜底）</li>
 *   <li>权限矩阵符合 DefaultPermissionPolicy：DOCTOR/NURSE 可读不可写；IT_OPS 可读可写</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ContextSnapshotControllerSecurityTest {

    private static final String VALID_BODY = "{\"patientId\":\"MPI-1\",\"orgUnitId\":\"ORG-1\","
        + "\"resources\":{\"patient\":null,\"encounters\":[],\"conditions\":[],"
        + "\"nursingAssessments\":[],\"observations\":[],\"diagnosticReports\":[],"
        + "\"medications\":[],\"procedures\":[],\"documents\":[],"
        + "\"carePlans\":[],\"followUps\":[],\"claims\":[]}}";

    @Autowired
    MockMvc mvc;

    @MockBean
    ContextSnapshotService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    // ─── context.write 权限矩阵 ──────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCannotCreateSnapshot() throws Exception {
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void nurseCannotCreateSnapshot() throws Exception {
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void itOpsCanReachCreateButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.create(any(), any())).thenReturn(null);
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void implementationEngineerCanReachCreateButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.create(any(), any())).thenReturn(null);
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    // ─── context.read 权限矩阵 ───────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void unmappedRoleIsForbiddenFromReading() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots/ctx-1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanReachReadButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots/ctx-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void nurseCanReachListButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots").param("patientId", "MPI-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void auditComplianceCanReachListButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    // ─── 写入接口禁止读权限角色 ─────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void auditComplianceCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void engineOperatorCanReachCreateButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.create(any(), any())).thenReturn(null);
        mvc.perform(post("/api/v1/engine/context/snapshots")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    // ─── diagnose 端点权限矩阵 ───────────────────────────────

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void diagnoseRequires403WithoutReadPerm() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots/ctx-1/diagnose"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanReachDiagnoseButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots/ctx-1/diagnose"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void auditComplianceCanReachDiagnoseButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/context/snapshots/ctx-1/diagnose"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
