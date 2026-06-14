package com.medkernel.engine.embed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmbedEngineControllerTest {

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
          "integrationMode": "SDK",
          "hook": "order-sign",
          "hookInstance": "hook-order-001",
          "parentOrigin": "https://his.hospital.com"
        }
        """;

    private static final String LAUNCH_EXCHANGE_BODY = """
        {
          "token": "tkn-123456",
          "integrationMode": "SDK",
          "hook": "order-sign",
          "hookInstance": "hook-order-001"
        }
        """;

    private static final String FEEDBACK_BODY = """
        {
          "token": "tkn-123456",
          "cardId": "card-123",
          "actionType": "ADOPT",
          "reason": "已采纳抗凝建议"
        }
        """;

    private static final String ORIGIN_BODY = """
        {
          "origin": "https://his.hospital.com"
        }
        """;

    @Test
    void generateToken_ReturnsOkWithResponse() throws Exception {
        EmbedLaunchTokenResponse mockResponse = new EmbedLaunchTokenResponse(
            "tkn-123456", Instant.now().plusSeconds(60), "/embed/launch?token=tkn-123456",
            EmbedIntegrationMode.SDK, "/api/v1/engine/embed/launch", "order-sign", "hook-order-001"
        );
        when(service.generateToken(any(EmbedLaunchTokenRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/engine/embed/launch-tokens")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_TOKEN_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value("tkn-123456"))
            .andExpect(jsonPath("$.data.embedUrl").value("/embed/launch?token=tkn-123456"))
            .andExpect(jsonPath("$.data.integrationMode").value("SDK"))
            .andExpect(jsonPath("$.data.launchEndpoint").value("/api/v1/engine/embed/launch"))
            .andExpect(jsonPath("$.data.hook").value("order-sign"))
            .andExpect(jsonPath("$.data.hookInstance").value("hook-order-001"));

        verify(service).generateToken(any(EmbedLaunchTokenRequest.class));
    }

    @Test
    void validateAndExchange_PostBodyReturnsOkWithThreeRouteContext() throws Exception {
        EmbedLaunchContextResponse mockResponse = new EmbedLaunchContextResponse(
            "DOCTOR-001", "clinical-decision-user", "tenant-1", "P1001", "E2001", "order-sign", true, "trace-123",
            EmbedIntegrationMode.SDK, "order-sign", "hook-order-001", EmbedModelStatus.MODEL_DISABLED,
            EmbedConnectionStatus.CONNECTED, "1.0", "https://his.hospital.com"
        );
        when(service.validateAndExchange(any(EmbedLaunchRequest.class)))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/engine/embed/launch")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LAUNCH_EXCHANGE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("DOCTOR-001"))
            .andExpect(jsonPath("$.data.patientId").value("P1001"))
            .andExpect(jsonPath("$.data.active").value(true))
            .andExpect(jsonPath("$.data.integrationMode").value("SDK"))
            .andExpect(jsonPath("$.data.hook").value("order-sign"))
            .andExpect(jsonPath("$.data.hookInstance").value("hook-order-001"))
            .andExpect(jsonPath("$.data.modelStatus").value("MODEL_DISABLED"))
            .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"))
            .andExpect(jsonPath("$.data.cdsHookVersion").value("1.0"))
            .andExpect(jsonPath("$.data.parentOrigin").value("https://his.hospital.com"));

        verify(service).validateAndExchange(any(EmbedLaunchRequest.class));
    }

    @Test
    void feedback_ReturnsOk() throws Exception {
        when(service.feedback(any(EmbedFeedbackRequest.class))).thenReturn(
            new EmbedFeedbackResponse(
                "tkn-123456", "card-123", "ADOPT", "ACCEPTED",
                EmbedConnectionStatus.NOT_CONNECTED, false, "HOST_CALLBACK_NOT_CONFIGURED", "trace-123"));

        mockMvc.perform(post("/api/v1/engine/embed/feedback")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(FEEDBACK_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cardId").value("card-123"))
            .andExpect(jsonPath("$.data.recommendationStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.callbackStatus").value("NOT_CONNECTED"))
            .andExpect(jsonPath("$.data.callbackDelivered").value(false))
            .andExpect(jsonPath("$.data.degradationReason").value("HOST_CALLBACK_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.data.traceId").value("trace-123"));

        verify(service).feedback(any(EmbedFeedbackRequest.class));
    }

    @Test
    void addOrigin_ReturnsOk() throws Exception {
        doNothing().when(service).addOrigin(any(EmbedOriginRequest.class));

        mockMvc.perform(post("/api/v1/engine/embed/origins")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ORIGIN_BODY))
            .andExpect(status().isOk());

        verify(service).addOrigin(any(EmbedOriginRequest.class));
    }

    @Test
    void getOrigins_ReturnsList() throws Exception {
        when(service.getOrigins()).thenReturn(List.of("https://his.hospital.com"));

        mockMvc.perform(get("/api/v1/engine/embed/origins")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("https://his.hospital.com"));

        verify(service).getOrigins();
    }
}
