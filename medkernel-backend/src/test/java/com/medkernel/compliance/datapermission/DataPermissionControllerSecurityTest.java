package com.medkernel.compliance.datapermission;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import com.medkernel.shared.security.DataAccessLevel;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SYS-06 PR1 数据权限控制器安全矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DataPermissionControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    DataPermissionService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("合规审计角色可到达数据权限列表，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDIT_COMPLIANCE")
    void listPolicies_auditRoleWithoutTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/data-permissions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("普通医生无 audit.read 权限，读取数据权限策略直接 403")
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void listPolicies_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/data-permissions"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("合规审计角色带租户上下文可读取数据权限策略")
    void listPolicies_auditRoleWithTenant_returns200() throws Exception {
        when(service.listPolicies(eq("t-1"), isNull(), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/v1/compliance/data-permissions")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "t-1")
                    .claim("group_id", "g-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "cardiology")
                    .claim("roles", List.of("audit_compliance")))
                    .authorities(new SimpleGrantedAuthority("ROLE_AUDIT_COMPLIANCE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("信息科可到达数据权限写接口，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void putPolicy_itOpsWithoutTenant_returns400() throws Exception {
        mvc.perform(put("/api/v1/compliance/data-permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("合规审计角色无 system.manage 权限，不能写数据权限策略")
    @WithMockUser(authorities = "ROLE_AUDIT_COMPLIANCE")
    void putPolicy_auditRole_returns403() throws Exception {
        mvc.perform(put("/api/v1/compliance/data-permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("医生可按当前组织域检查目标数据权限，跨科室目标返回不允许")
    void checkPolicy_doctorWithDepartment_returnsDeniedDecision() throws Exception {
        when(service.evaluate(
                argThat(scope -> scope.level() == DataAccessLevel.DEPARTMENT
                    && "cardiology".equals(scope.scope().departmentId())),
                argThat(check -> "t-1".equals(check.tenantId())
                    && "act10_patient_scope".equals(check.resourceType())
                    && check.action() == DataPermissionAction.READ
                    && "respiratory-icu".equals(check.targetScope().departmentId()))))
            .thenReturn(new DataPermissionDecision(
                "dperm-act10-patient-scope-read",
                "act10_patient_scope",
                DataPermissionAction.READ,
                DataAccessLevel.DEPARTMENT,
                false,
                List.of("patientId", "encounterId"),
                List.of()
            ));

        mvc.perform(post("/api/v1/compliance/data-permissions:check")
                .with(jwt().jwt(token -> token
                    .subject("doctor-act10")
                    .claim("tenant_id", "t-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "cardiology")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resourceType": "act10_patient_scope",
                      "action": "READ",
                      "hospitalId": "h-1",
                      "departmentId": "respiratory-icu",
                      "requestedColumns": ["patientId", "encounterId"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rowAllowed").value(false))
            .andExpect(jsonPath("$.data.requiredLevel").value("DEPARTMENT"));
    }

    private String policyBody() {
        return """
            {
              "resourceType": "clinical_case",
              "action": "READ",
              "minDataLevel": "DEPARTMENT",
              "allowedColumns": ["patientId", "diagnosisName"],
              "groupId": "g-1",
              "hospitalId": "h-1",
              "departmentId": "cardiology",
              "status": "ACTIVE",
              "reason": "SYS-06 PR1 行列权限基线"
            }
            """;
    }
}
