package com.medkernel.engine.embed;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmbedEngineControllerSecurityTest {

    private static final String TRUSTED_ORIGIN = "https://his.hospital.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmbedEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String LAUNCH_TOKEN_BODY = """
        {
          "roleCode": "clinical-decision-user",
          "patientId": "P1001",
          "encounterId": "E2001",
          "triggerPoint": "ORDER_SIGN",
          "expireSeconds": 60,
          "integrationMode": "IFRAME",
          "hook": "order-sign",
          "hookInstance": "hook-order-001",
          "parentOrigin": "https://his.hospital.com"
        }
        """;

    private static final String LAUNCH_EXCHANGE_BODY = """
        {
          "token": "tkn-123456",
          "integrationMode": "IFRAME",
          "hook": "order-sign",
          "hookInstance": "hook-order-001"
        }
        """;

    @Test
    void testLaunchTokenWithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_TOKEN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLaunchTokenWithWriteRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch-tokens")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_TOKEN_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void testLaunchTokenWithReadOnlyRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch-tokens")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_TOKEN_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void testLaunchTokenWithWriteRoleButMissingTenant_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch-tokens")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_TOKEN_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void launchExchangeWithoutAuth_ShouldUseTokenAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_EXCHANGE_BODY))
            .andExpect(status().isOk());
    }

    @Test
    void launchExchangeWithReadRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_EXCHANGE_BODY))
            .andExpect(status().isOk());
    }

    @Test
    void launchExchangeDoesNotDependOnBrowserOriginHeader() throws Exception {
        mockMvc.perform(post("/api/v1/engine/embed/launch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_EXCHANGE_BODY))
            .andExpect(status().isOk());
    }
}
