package com.medkernel.engine.llm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 全业务模型增强接入矩阵控制器权限安全测试（LLM-05，{@code llm.enhancement.manage} 归平台治理管理员）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelEnhancementMatrixControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelEnhancementMatrixService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = """
        {
          "businessName": "临床规则草案拟定",
          "capabilityCode": "rule.draft",
          "b0Path": "确定性规则模板基线",
          "accessStatus": "ACTIVE",
          "enabled": true,
          "sortOrder": 40
        }
        """;

    @Test
    void clinicalUserCannotUpsertMatrixEntry() throws Exception {
        mockMvc.perform(put("/api/v1/model-enhancement-matrix/rule")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCannotUpsertMatrixEntry() throws Exception {
        // LLM-05 矩阵＝平台治理管理员职责（模型网关全局目录），集成运维员（管 provider/出域）无权。
        mockMvc.perform(put("/api/v1/model-enhancement-matrix/rule")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformGovernanceAdminCanUpsertMatrixEntry() throws Exception {
        mockMvc.perform(put("/api/v1/model-enhancement-matrix/rule")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("platform-governance-admin")))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_GOVERNANCE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }
}
