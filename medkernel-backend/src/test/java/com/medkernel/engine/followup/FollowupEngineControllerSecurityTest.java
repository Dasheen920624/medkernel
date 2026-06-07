package com.medkernel.engine.followup;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

/**
 * 随访引擎 Controller 安全矩阵测试。
 *
 * <p>验证未认证用户和不同角色用户对随访 API 的访问控制：
 * <ul>
 *   <li>未认证 → 401 Unauthorized</li>
 *   <li>有 followup.write 权限的角色（医务处）→ 200 OK</li>
 *   <li>有 followup.read 权限的角色（医生）→ GET 200 OK，POST 403 Forbidden</li>
 * </ul>
 *
 * <p>权限模型：{@code @perm.has('followup.write')} 基于 RoleCode → DefaultPermissionPolicy 角色权限映射推导。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FollowupEngineControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowupEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String GENERATE_BODY = """
        {
          "contextSnapshotId": "ctx-active-1",
          "riskLevel": "HIGH",
          "taskTypes": ["QUESTIONNAIRE", "OUTPATIENT"]
        }
        """;

    @Test
    void testGenerateWithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGenerateWithWriteRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDetailWithReadRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/engine/followup/plans/P1000")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void testGenerateWithReadOnlyRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGenerateWithWriteRoleButMissingTenant_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void testTopLevelQuestionnaireWithReadOnlyRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/questionnaires")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskId": "TASK-001",
                      "questionnaireTemplateId": "Q-TPL-1",
                      "formData": "{\\"title\\": \\"出院后症状随访\\"}",
                      "idempotencyKey": "questionnaire-key-1"
                    }
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void testTopLevelResultWithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "planId": "PLAN-001",
                      "taskId": "TASK-001",
                      "questionnaireId": "FQ-001",
                      "resultPayload": "{\\"painScore\\": 2}",
                      "idempotencyKey": "result-key-1"
                    }
                    """))
                .andExpect(status().isUnauthorized());
    }
}
