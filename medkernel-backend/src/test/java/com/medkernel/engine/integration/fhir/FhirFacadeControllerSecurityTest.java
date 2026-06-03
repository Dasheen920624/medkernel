package com.medkernel.engine.integration.fhir;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medkernel.shared.context.RequestContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FhirFacadeControllerSecurityTest {

    private static final String OBSERVATION_BODY = """
        {
          "resourceType": "Observation",
          "id": "obs-1",
          "code": {"coding": [{"system": "http://loinc.org", "code": "718-7"}]},
          "subject": {"reference": "Patient/MPI-001"},
          "valueQuantity": {"value": 128, "unit": "g/L"}
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    FhirFacadeService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    void anonymousCannotReadFhirMetadata() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/fhir/R4/metadata"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachMetadataButDataScopeRequiresTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/fhir/R4/metadata"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void anonymousCannotCreateFhirResource() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/fhir/R4/Observation")
                .contentType("application/fhir+json")
                .content(OBSERVATION_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotCreateFhirResourceThroughIntegrationFacade() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/fhir/R4/Observation")
                .contentType("application/fhir+json")
                .content(OBSERVATION_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachCreateButDataScopeRequiresTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/fhir/R4/Observation")
                .contentType("application/fhir+json")
                .header("X-MedKernel-Fhir-Adapter", "fhir-hub")
                .header("X-MedKernel-Timestamp", "1780456123")
                .header("X-MedKernel-Signature", "sha256=dummy")
                .content(OBSERVATION_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
