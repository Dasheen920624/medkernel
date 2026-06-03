package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.TerminologyMappingPort;
import org.junit.jupiter.api.Test;

class FhirR5CanonicalMapperTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapsCanonicalPatientToR5PatientWithVersionSpecificMetadata() throws Exception {
        FhirR5CanonicalMapper mapper = new FhirR5CanonicalMapper(json, terminologyReturning("VALID"));
        CanonicalResource canonical = new CanonicalResource(
            null,
            "pat-r5",
            "snapshot-1",
            "tenant-A",
            CanonicalResourceType.PATIENT,
            """
                {
                  "mpi": "MPI-R5",
                  "name": "李四",
                  "birthDate": "1991-02-03",
                  "gender": "female",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "PAT-R5",
                  "mappedVersion": "v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """,
            "HIS",
            "PAT-R5",
            "v1",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:01Z"),
            QualityStatus.VALID,
            0,
            "trace-r5-patient");

        FhirResourceMappingResult result = mapper.toR5(canonical);

        JsonNode patient = result.resource();
        assertThat(patient.path("resourceType").asText()).isEqualTo("Patient");
        assertThat(patient.path("id").asText()).isEqualTo("pat-r5");
        assertThat(patient.path("meta").path("source").asText()).isEqualTo("canonical_resource/pat-r5");
        assertThat(patient.path("meta").path("profile").get(0).asText())
            .isEqualTo("http://hl7.org/fhir/5.0/StructureDefinition/Patient");
        assertThat(patient.path("identifier").get(0).path("value").asText()).isEqualTo("MPI-R5");
        assertThat(patient.path("name").get(0).path("text").asText()).isEqualTo("李四");
        assertThat(patient.path("gender").asText()).isEqualTo("female");
        assertThat(patient.path("birthDate").asText()).isEqualTo("1991-02-03");
        assertThat(result.mappingRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void mapsR5ObservationAndReturnsWarningWhenTerminologyIsUnmapped() throws Exception {
        FhirR5CanonicalMapper mapper = new FhirR5CanonicalMapper(json, terminologyReturning("UNKNOWN"));
        JsonNode observation = json.readTree("""
            {
              "resourceType": "Observation",
              "id": "obs-r5",
              "code": {
                "coding": [
                  {
                    "system": "urn:local:lis",
                    "code": "HB",
                    "display": "血红蛋白"
                  }
                ]
              },
              "effectiveDateTime": "2026-06-03T00:00:00Z",
              "valueQuantity": {
                "value": 128,
                "unit": "g/L"
              }
            }
            """);

        CanonicalResourceMappingResult result = mapper.fromR5(new FhirCanonicalMappingRequest(
            "tenant-A",
            "snapshot-r5",
            4,
            "trace-r5",
            Instant.parse("2026-06-03T00:00:10Z"),
            observation));

        CanonicalResource canonical = result.resource();
        assertThat(canonical.sourceSystem()).isEqualTo("FHIR_R5");
        assertThat(canonical.mappedVersion()).isEqualTo("FHIR_R5:Observation");
        assertThat(canonical.qualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(result.mappingRate()).isEqualByComparingTo(new BigDecimal("0.9000"));
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo("warning");
            assertThat(issue.diagnostics()).contains("TERM-01", "urn:local:lis", "HB");
        });
    }

    private static TerminologyMappingPort terminologyReturning(String status) {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }
}
