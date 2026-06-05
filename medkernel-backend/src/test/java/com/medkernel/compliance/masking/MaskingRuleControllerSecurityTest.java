package com.medkernel.compliance.masking;

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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SYS-06 PR2 脱敏规则控制器安全矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MaskingRuleControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MaskingService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("合规审计角色可到达脱敏规则列表，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_AUDIT_COMPLIANCE")
    void listRules_auditRoleWithoutTenant_returns400() throws Exception {
        mvc.perform(get("/api/v1/compliance/masking-rules"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("普通医生无 audit.read 权限，读取脱敏规则直接 403")
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void listRules_doctorRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance/masking-rules"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("合规审计角色带租户上下文可读取脱敏规则")
    void listRules_auditRoleWithTenant_returns200() throws Exception {
        when(service.listRules(eq("t-1"), isNull(), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/v1/compliance/masking-rules")
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
    @DisplayName("信息科可到达脱敏规则写接口，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void putRule_itOpsWithoutTenant_returns400() throws Exception {
        mvc.perform(put("/api/v1/compliance/masking-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("合规审计角色无 system.manage 权限，不能写脱敏规则")
    @WithMockUser(authorities = "ROLE_AUDIT_COMPLIANCE")
    void putRule_auditRole_returns403() throws Exception {
        mvc.perform(put("/api/v1/compliance/masking-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody()))
            .andExpect(status().isForbidden());
    }

    private String ruleBody() {
        return """
            {
              "resourceType": "clinical_case",
              "fieldName": "patientPhone",
              "scenarioCode": "DEFAULT",
              "strategy": "KEEP_LAST",
              "maskChar": "*",
              "prefixKeep": 0,
              "suffixKeep": 4,
              "status": "ACTIVE",
              "reason": "SYS-06 PR2 后端脱敏规则基线"
            }
            """;
    }
}
