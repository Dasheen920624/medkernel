package com.medkernel.engine.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ClinicalEventControllerSecurityTest {

    private static final String VALID_BODY = """
        {
          "eventId": "evt-1",
          "eventType": "DIAGNOSIS",
          "patientId": "MPI-1",
          "encounterId": "ENC-1",
          "sourceSystem": "HIS",
          "packageVersion": "kpv-1",
          "triggerPoint": "patient-view",
          "occurredAt": "2026-05-27T01:00:00Z",
          "payload": {"diagnosisCode": "I21.0"}
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    ClinicalEventService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotCreateClinicalEvent() throws Exception {
        mvc.perform(post("/api/v1/engine/events")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachAsyncCreateButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.receiveAsync(any())).thenReturn(accepted());
        mvc.perform(post("/api/v1/engine/events/async")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachClinicalEventsCustomerPathButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.receive(any())).thenReturn(accepted());
        mvc.perform(post("/api/v1/engine/clinical-events")
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachClinicalEventsBatchSuffixPathButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.receiveBatch(any())).thenReturn(
            new ClinicalEventBatchResponse("batch-1", java.util.List.of(), java.util.List.of(),
                0, 0, 0, ClinicalEventBatchStatus.COMPLETED, "trace"));
        mvc.perform(post("/api/v1/engine/clinical-events:batch")
                .contentType("application/json")
                .content("{\"events\":[" + VALID_BODY + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachClinicalEventsReplaySuffixPathButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.replay(anyString())).thenReturn(
            new ClinicalEventReplayResponse("evt-1", "evt-replay-1",
                ClinicalEventStatus.RECEIVED, "trace"));
        mvc.perform(post("/api/v1/engine/clinical-events:replay")
                .contentType("application/json")
                .content("{\"sourceEventId\":\"evt-1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachClinicalEventsDeadLetterReplayButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.replayDeadLetter(anyString())).thenReturn(
            new ClinicalEventReplayResponse("evt-dead", "evt-replay-1",
                ClinicalEventStatus.RECEIVED, "trace"));
        mvc.perform(post("/api/v1/engine/clinical-events/dead-letter/evt-dead/replay"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanReachReadButDataScopeFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/events/evt-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void unmappedRoleCannotReadClinicalEvent() throws Exception {
        mvc.perform(get("/api/v1/engine/events/evt-1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDIT_COMPLIANCE")
    void auditComplianceCannotReplayClinicalEvent() throws Exception {
        mvc.perform(post("/api/v1/engine/events/evt-1/replay"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachReplayButDataScopeFailsOnMissingTenant() throws Exception {
        when(service.replay(anyString())).thenReturn(
            new ClinicalEventReplayResponse("evt-1", "evt-replay-1",
                ClinicalEventStatus.RECEIVED, "trace"));
        mvc.perform(post("/api/v1/engine/events/evt-1/replay"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    private ClinicalEventAcceptedResponse accepted() {
        return new ClinicalEventAcceptedResponse(
            "evt-1", ClinicalEventStatus.RECEIVED, "digest", "trace", Instant.now());
    }
}
