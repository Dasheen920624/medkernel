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
    void declaresR4AndR5MappingSurfaceForPr4CanonicalResourcesWithoutRuntimeInteractions() {
        JsonNode r4 = service.mappingCapability(FhirVersion.R4);
        JsonNode r5 = service.mappingCapability(FhirVersion.R5);

        assertThat(r4.path("resourceType").asText()).isEqualTo("CapabilityStatement");
        assertThat(r4.path("software").path("version").asText()).isEqualTo("OPT-01-PR4");
        assertThat(r4.path("fhirVersion").asText()).isEqualTo("4.0.1");
        assertThat(r5.path("fhirVersion").asText()).isEqualTo("5.0.0");
        assertThat(r4.path("status").asText()).isEqualTo("active");
        assertThat(r4.path("kind").asText()).isEqualTo("capability");
        assertThat(r4.path("format")).anySatisfy(format -> assertThat(format.asText()).isEqualTo("json"));
        assertThat(r4.path("implementation").path("description").asText())
            .contains("OPT-01 PR4", "确定性映射", "医师确认");

        JsonNode resources = r4.path("rest").get(0).path("resource");
        assertThat(resourceTypes(resources)).contains(
            "Patient",
            "Encounter",
            "Condition",
            "AllergyIntolerance",
            "Observation",
            "Medication",
            "Procedure",
            "CarePlan",
            "DiagnosticReport",
            "DocumentReference"
        );
        assertThat(mappingDirection(resources, "Patient")).isEqualTo("BIDIRECTIONAL");
        assertThat(mappingDirection(resources, "AllergyIntolerance")).isEqualTo("BIDIRECTIONAL");
        assertThat(mappingDirection(resources, "Observation")).isEqualTo("BIDIRECTIONAL");
        assertThat(interactions(resources)).doesNotContain("create");
    }

    @Test
    void declaresPr4RuntimeSurfaceForTenCoreResources() {
        JsonNode statement = service.runtimeCapability(FhirVersion.R4);

        assertThat(statement.path("software").path("version").asText()).isEqualTo("OPT-01-PR4");
        assertThat(statement.path("implementation").path("description").asText())
            .contains("11 类核心 FHIR 资源", "read", "search", "create", "医师确认", "NOT_CONNECTED");
        JsonNode resources = statement.path("rest").get(0).path("resource");
        assertThat(resourceTypes(resources)).contains(
            "Patient",
            "Encounter",
            "Condition",
            "AllergyIntolerance",
            "Observation",
            "Medication",
            "Procedure",
            "CarePlan",
            "ServiceRequest",
            "DiagnosticReport",
            "DocumentReference"
        );
        assertThat(interactionsFor(resources, "Patient")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "Condition")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "AllergyIntolerance")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "Observation")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "DiagnosticReport")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "DocumentReference")).containsExactly("read", "search", "create");
        assertThat(interactionsFor(resources, "ServiceRequest")).containsExactly("read", "search", "create");
        assertThat(documentationFor(resources, "ServiceRequest")).contains("医师确认").contains("不自动写申请单");
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
