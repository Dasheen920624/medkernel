package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FhirOperationOutcomeFactoryTest {

    private final FhirOperationOutcomeFactory factory =
        new FhirOperationOutcomeFactory(new ObjectMapper().findAndRegisterModules());

    @Test
    void rendersIssuesAsFHIRJsonOperationOutcome() {
        JsonNode outcome = factory.fromIssues(List.of(new FhirOperationOutcomeIssue(
            "warning",
            "not-supported",
            "TERM-01 未找到 LIS:HB 到 LOINC 的已确认映射")));

        assertThat(outcome.path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(outcome.path("issue")).hasSize(1);
        JsonNode issue = outcome.path("issue").get(0);
        assertThat(issue.path("severity").asText()).isEqualTo("warning");
        assertThat(issue.path("code").asText()).isEqualTo("not-supported");
        assertThat(issue.path("diagnostics").asText()).contains("TERM-01", "LIS:HB", "LOINC");
    }
}
