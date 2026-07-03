package com.medkernel.engine.runtime.diagnostics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运行诊断只暴露治理后的 API 契约视图。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeDiagnosticsControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void apiContractDirectoryRequiresAuthenticationAndSystemReadPermission() throws Exception {
        mvc.perform(get("/api/v1/system/runtime-diagnostics/api-contracts"))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/system/runtime-diagnostics/api-contracts")
                .with(jwt().jwt(token -> token.subject("doctor-1").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void apiContractDirectoryReturnsSanitizedServiceContracts() throws Exception {
        mvc.perform(get("/api/v1/system/runtime-diagnostics/api-contracts")
                .with(jwt().jwt(token -> token.subject("ops-1").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("runtime-operations")))
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("observability-diagnose")))
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("third-party-knowledge-runtime")))
            .andExpect(jsonPath("$.data.contracts[?(@.id == 'diagnosis-knowledge')].title",
                hasItem("诊断知识库服务")))
            .andExpect(jsonPath("$.data.contracts[?(@.id == 'quality-dashboard')].title",
                hasItem("质量管理概览服务")))
            .andExpect(jsonPath("$.data.contracts[?(@.id == 'terminology')].title",
                hasItem("术语字典服务")))
            .andExpect(jsonPath("$.data.contracts[?(@.id == 'workflow-notification')].title",
                hasItem("消息通知服务")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].contractVersion",
                hasItem("v1")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].openApiDocumentUrl",
                hasItem("/v3/api-docs/medkernel-third-party-integration")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].fieldContractUrl",
                hasItem("/api/v1/engine/integration/data-contract")))
            .andExpect(jsonPath("$.data.contracts[*].basePath", hasItem("/api/v1/system")))
            .andExpect(jsonPath("$.data.contracts[*].permissions[*].code", hasItem("system.read")))
            .andExpect(jsonPath("$.data.contracts[*].controllerClassName").doesNotExist())
            .andExpect(content().string(not(containsString("controllerClassName"))))
            .andExpect(content().string(not(containsString("com.medkernel"))))
            .andExpect(content().string(not(containsString("passwordHash"))))
            .andExpect(content().string(not(containsString("accessToken"))))
            .andExpect(content().string(not(containsString("refreshToken"))))
            .andExpect(content().string(not(containsString("诊断知识维护服务"))))
            .andExpect(content().string(not(containsString("质控驾驶舱"))))
            .andExpect(content().string(not(containsString("字典映射"))))
            .andExpect(content().string(not(containsString("临床通知中心服务"))));
    }
}
