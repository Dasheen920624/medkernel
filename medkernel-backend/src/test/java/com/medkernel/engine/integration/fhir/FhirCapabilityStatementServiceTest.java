package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FhirCapabilityStatementServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final FhirCapabilityStatementService service = new FhirCapabilityStatementService(json);

    @Test
    void declaresR4AndR5MappingSurfaceWithoutOpeningUnsafeRuntimeCreates() {
        JsonNode r4 = service.mappingCapability(FhirVersion.R4);
        JsonNode r5 = service.mappingCapability(FhirVersion.R5);

        assertThat(r4.path("resourceType").asText()).isEqualTo("CapabilityStatement");
        assertThat(r4.path("fhirVersion").asText()).isEqualTo("4.0.1");
        assertThat(r5.path("fhirVersion").asText()).isEqualTo("5.0.0");
        assertThat(r4.path("status").asText()).isEqualTo("active");
        assertThat(r4.path("kind").asText()).isEqualTo("capability");
        assertThat(r4.path("format")).anySatisfy(format -> assertThat(format.asText()).isEqualTo("json"));
        assertThat(r4.path("implementation").path("description").asText())
            .contains("OPT-01 PR2", "PR3");

        JsonNode resources = r4.path("rest").get(0).path("resource");
        assertThat(resourceTypes(resources)).containsExactly("Patient", "Observation");
        assertThat(mappingDirection(resources, "Patient")).isEqualTo("OUTBOUND");
        assertThat(mappingDirection(resources, "Observation")).isEqualTo("INBOUND");
        assertThat(interactions(resources)).doesNotContain("create");
    }

    @Test
    void declaresPr3RuntimeSurfaceWithoutClaimingUnsupportedTenResources() {
        JsonNode statement = service.runtimeCapability(FhirVersion.R4);

        assertThat(statement.path("software").path("version").asText()).isEqualTo("OPT-01-PR3");
        assertThat(statement.path("implementation").path("description").asText())
            .contains("Observation create", "医师确认", "NOT_CONNECTED");
        JsonNode resources = statement.path("rest").get(0).path("resource");
        assertThat(resourceTypes(resources)).containsExactly("Observation", "MedicationRequest", "ServiceRequest");
        assertThat(interactionsFor(resources, "Observation")).containsExactly("create");
        assertThat(interactionsFor(resources, "MedicationRequest")).containsExactly("create");
        assertThat(interactionsFor(resources, "ServiceRequest")).containsExactly("create");
        assertThat(resourceTypes(resources)).doesNotContain("Condition", "DiagnosticReport", "DocumentReference");
        assertThat(documentationFor(resources, "MedicationRequest")).contains("医师确认").contains("不自动写医嘱");
    }

    private static Iterable<String> resourceTypes(JsonNode resources) {
        return StreamSupport.stream(resources.spliterator(), false)
            .map(resource -> resource.path("type").asText())
            .toList();
    }

    private static Iterable<String> interactions(JsonNode resources) {
        return StreamSupport.stream(resources.spliterator(), false)
            .flatMap(resource -> StreamSupport.stream(resource.path("interaction").spliterator(), false))
            .map(interaction -> interaction.path("code").asText())
            .toList();
    }

    private static Iterable<String> interactionsFor(JsonNode resources, String type) {
        return StreamSupport.stream(resources.spliterator(), false)
            .filter(resource -> type.equals(resource.path("type").asText()))
            .flatMap(resource -> StreamSupport.stream(resource.path("interaction").spliterator(), false))
            .map(interaction -> interaction.path("code").asText())
            .toList();
    }

    private static String documentationFor(JsonNode resources, String type) {
        return StreamSupport.stream(resources.spliterator(), false)
            .filter(resource -> type.equals(resource.path("type").asText()))
            .map(resource -> resource.path("documentation").asText())
            .findFirst()
            .orElseThrow();
    }

    private static String mappingDirection(JsonNode resources, String type) {
        return StreamSupport.stream(resources.spliterator(), false)
            .filter(resource -> type.equals(resource.path("type").asText()))
            .flatMap(resource -> StreamSupport.stream(resource.path("extension").spliterator(), false))
            .filter(extension -> "urn:medkernel:fhir:mapping-direction".equals(extension.path("url").asText()))
            .map(extension -> extension.path("valueCode").asText())
            .findFirst()
            .orElseThrow();
    }
}
