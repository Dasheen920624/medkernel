package com.medkernel.engine.developer;

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
 * D6 DEVCON-01：开发者控制台只暴露治理后的 API 契约视图。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeveloperConsoleControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void apiContractDirectoryRequiresAuthenticationAndSystemReadPermission() throws Exception {
        mvc.perform(get("/api/v1/system/dev-console/api-contracts"))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/system/dev-console/api-contracts")
                .with(jwt().jwt(token -> token.subject("doctor-1").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void apiContractDirectoryReturnsSanitizedServiceContracts() throws Exception {
        mvc.perform(get("/api/v1/system/dev-console/api-contracts")
                .with(jwt().jwt(token -> token.subject("ops-1").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("runtime-operations")))
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("observability-diagnose")))
            .andExpect(jsonPath("$.data.contracts[*].id", hasItem("third-party-knowledge-runtime")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].contractVersion",
                hasItem("v1")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].openApiDocumentUrl",
                hasItem("/v3/api-docs/medkernel-third-party-integration")))
            .andExpect(jsonPath(
                "$.data.contracts[?(@.id == 'third-party-knowledge-runtime')].fieldContractUrl",
                hasItem("/api/v1/engine/integration/data-contract?packageVersion={packageVersion}")))
            .andExpect(jsonPath("$.data.contracts[*].basePath", hasItem("/api/v1/system")))
            .andExpect(jsonPath("$.data.contracts[*].permissions[*].code", hasItem("system.read")))
            .andExpect(jsonPath("$.data.contracts[*].controllerClassName").doesNotExist())
            .andExpect(content().string(not(containsString("controllerClassName"))))
            .andExpect(content().string(not(containsString("com.medkernel"))))
            .andExpect(content().string(not(containsString("passwordHash"))))
            .andExpect(content().string(not(containsString("accessToken"))))
            .andExpect(content().string(not(containsString("refreshToken"))));
    }
}
