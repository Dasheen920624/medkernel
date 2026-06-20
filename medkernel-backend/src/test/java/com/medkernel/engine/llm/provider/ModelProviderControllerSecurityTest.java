package com.medkernel.engine.llm.provider;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelProviderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelProviderGovernanceService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = """
        {
          "providerType": "OLLAMA",
          "endpointUri": "http://127.0.0.1:11434",
          "modelVersion": "qwen2.5:7b"
        }
        """;

    private static final String ACTIVATION_BODY = """
        {
          "capabilityCode": "rule.draft",
          "reason": "按变更单受控启停",
          "expectedVersion": 5,
          "confirmedHighRisk": true
        }
        """;

    private static final String CREDENTIAL_BODY = """
        {
          "credential": "sk-fake-provider-key-1234",
          "reason": "登记生产模型凭据",
          "confirmedHighRisk": true
        }
        """;

    private static final String CREDENTIAL_REMOVAL_BODY = """
        {
          "reason": "撤销失效的生产模型凭据",
          "expectedVersion": 3,
          "confirmedHighRisk": true
        }
        """;

    @Test
    void clinicalUserCannotConfigureProvider() throws Exception {
        mockMvc.perform(put("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanConfigureProvider() throws Exception {
        when(service.upsertProvider(
            org.mockito.ArgumentMatchers.eq("ollama-local"),
            any(ModelProviderUpsertRequest.class)))
            .thenReturn(providerWithCredential());
        when(service.getProvider("ollama-local")).thenReturn(sanitizedView());

        mockMvc.perform(put("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(content().string(not(containsString("credentialRef"))))
                .andExpect(content().string(not(containsString("MODEL_API_KEY"))));
    }

    @Test
    void clinicalUserCannotReadProviderGovernanceSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorReadsSanitizedProviderGovernanceSnapshot() throws Exception {
        when(service.getProvider("ollama-local")).thenReturn(new ModelProviderGovernanceView(
            "ollama-local", "OLLAMA", "http://127.0.0.1:11434", true,
            "VAULT", "1234", 3L, Instant.parse("2026-06-20T05:00:00Z"), "ops-001",
            "qwen2.5:7b", false, "HEALTHY", 7L, null, "ops-001"));

        mockMvc.perform(get("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(jsonPath("$.data.credentialLast4").value("1234"))
                .andExpect(content().string(not(containsString("credentialRef"))));
    }

    @Test
    void integrationOperatorCanListProvidersWithServerPagination() throws Exception {
        when(service.listProviders(any(PageRequest.class))).thenReturn(PageResponse.of(
            List.of(sanitizedView()), new PageRequest(1, 20, null), 1));

        mockMvc.perform(get("/api/v1/model-providers?page=1&size=20")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].providerCode").value("ollama-local"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(content().string(not(containsString("credentialRef"))));
    }

    @Test
    void qualityGovernorCanReadSanitizedProvidersForMedicalEvaluation() throws Exception {
        when(service.listProviders(any(PageRequest.class))).thenReturn(PageResponse.of(
            List.of(sanitizedView()), new PageRequest(1, 20, null), 1));

        mockMvc.perform(get("/api/v1/model-providers?page=1&size=20")
                .with(jwt().jwt(token -> token
                    .subject("quality-reviewer").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].providerCode").value("ollama-local"))
                .andExpect(jsonPath("$.data.items[0].credentialConfigured").value(true))
                .andExpect(content().string(not(containsString("credentialRef"))))
                .andExpect(content().string(not(containsString("MODEL_API_KEY"))));
    }

    @Test
    void qualityGovernorCannotMutateProviderConfiguration() throws Exception {
        mockMvc.perform(put("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("quality-reviewer").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void clinicalUserCannotManageProviderCredential() throws Exception {
        mockMvc.perform(put("/api/v1/model-providers/ollama-local/credential")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(CREDENTIAL_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanRotateAndRemoveCredentialWithoutSecretEcho() throws Exception {
        when(service.saveCredential(
            org.mockito.ArgumentMatchers.eq("ollama-local"),
            any(ModelProviderCredentialUpsertRequest.class))).thenReturn(sanitizedView());
        when(service.removeCredential(
            org.mockito.ArgumentMatchers.eq("ollama-local"),
            any(ModelProviderCredentialRemovalRequest.class))).thenReturn(sanitizedView());

        mockMvc.perform(put("/api/v1/model-providers/ollama-local/credential")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(CREDENTIAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialLast4").value("1234"))
                .andExpect(content().string(not(containsString("sk-fake-provider-key-1234"))));

        mockMvc.perform(delete("/api/v1/model-providers/ollama-local/credential")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(CREDENTIAL_REMOVAL_BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("credentialRef"))));
    }

    @Test
    void credentialMutationRejectsSuperficialReasonAtApiBoundary() throws Exception {
        String superficialReason = """
            {
              "credential": "sk-fake-provider-key-1234",
              "reason": "轮换",
              "confirmedHighRisk": true
            }
            """;

        mockMvc.perform(put("/api/v1/model-providers/ollama-local/credential")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(superficialReason))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clinicalUserCannotEnableProvider() throws Exception {
        mockMvc.perform(post("/api/v1/model-providers/ollama-local/enable")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(ACTIVATION_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanEnableAndDisableProvider() throws Exception {
        mockMvc.perform(post("/api/v1/model-providers/ollama-local/enable")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(ACTIVATION_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/model-providers/ollama-local/disable")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(ACTIVATION_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void activationRequestRequiresReasonVersionAndConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/model-providers/ollama-local/enable")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clinicalUserCannotProbeProviderHealth() throws Exception {
        mockMvc.perform(post("/api/v1/model-providers/ollama-local/health-check")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanProbeProviderHealth() throws Exception {
        when(service.checkHealth("ollama-local")).thenReturn(providerWithCredential());
        when(service.getProvider("ollama-local")).thenReturn(sanitizedView());

        mockMvc.perform(post("/api/v1/model-providers/ollama-local/health-check")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(content().string(not(containsString("credentialRef"))))
                .andExpect(content().string(not(containsString("MODEL_API_KEY"))));
    }

    private ModelProviderConfig providerWithCredential() {
        return new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            "qwen2.5:7b", "N", "HEALTHY",
            null, "ops-001", null, "ops-001", 7L);
    }

    private ModelProviderGovernanceView sanitizedView() {
        return new ModelProviderGovernanceView(
            "ollama-local", "OLLAMA", "http://127.0.0.1:11434", true,
            "VAULT", "1234", 3L, Instant.parse("2026-06-20T05:00:00Z"), "ops-001",
            "qwen2.5:7b", false, "HEALTHY", 7L, null, "ops-001");
    }
}
