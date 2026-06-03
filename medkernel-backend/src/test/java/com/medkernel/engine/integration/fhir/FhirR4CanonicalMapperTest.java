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

class FhirR4CanonicalMapperTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final FhirR4CanonicalMapper mapper = new FhirR4CanonicalMapper(json, terminologyReturning("UNKNOWN"));

    @Test
    void mapsCanonicalPatientToR4PatientWithoutInventingFields() throws Exception {
        CanonicalResource canonical = new CanonicalResource(
            null,
            "pat-1",
            "snapshot-1",
            "tenant-A",
            CanonicalResourceType.PATIENT,
            """
                {
                  "mpi": "MPI-001",
                  "name": "张三",
                  "birthDate": "1980-01-01",
                  "gender": "male",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "PAT-001",
                  "mappedVersion": "v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """,
            "HIS",
            "PAT-001",
            "v1",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:01Z"),
            QualityStatus.VALID,
            0,
            "trace-patient");

        FhirResourceMappingResult result = mapper.toR4(canonical);

        JsonNode patient = result.resource();
        assertThat(patient.path("resourceType").asText()).isEqualTo("Patient");
        assertThat(patient.path("id").asText()).isEqualTo("pat-1");
        assertThat(patient.path("identifier").get(0).path("system").asText()).isEqualTo("urn:medkernel:mpi");
        assertThat(patient.path("identifier").get(0).path("value").asText()).isEqualTo("MPI-001");
        assertThat(patient.path("name").get(0).path("text").asText()).isEqualTo("张三");
        assertThat(patient.path("gender").asText()).isEqualTo("male");
        assertThat(patient.path("birthDate").asText()).isEqualTo("1980-01-01");
        assertThat(patient.path("meta").path("source").asText()).isEqualTo("canonical_resource/pat-1");
        assertThat(result.mappingRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void mapsR4ObservationToCanonicalResourceAndPreservesCodingWarning() throws Exception {
        JsonNode observation = json.readTree("""
            {
              "resourceType": "Observation",
              "id": "obs-1",
              "status": "final",
              "code": {
                "coding": [
                  {
                    "system": "urn:local:lis",
                    "code": "HB",
                    "display": "血红蛋白"
                  }
                ],
                "text": "血红蛋白"
              },
              "subject": {"reference": "Patient/MPI-001"},
              "effectiveDateTime": "2026-06-03T00:00:00Z",
              "valueQuantity": {
                "value": 128,
                "unit": "g/L",
                "system": "http://unitsofmeasure.org",
                "code": "g/L"
              }
            }
            """);

        CanonicalResourceMappingResult result = mapper.fromR4(new FhirCanonicalMappingRequest(
            "tenant-A",
            "snapshot-fhir-1",
            2,
            "trace-fhir",
            Instant.parse("2026-06-03T00:00:10Z"),
            observation));

        CanonicalResource canonical = result.resource();
        assertThat(canonical.resourceId()).isEqualTo("obs-1");
        assertThat(canonical.snapshotId()).isEqualTo("snapshot-fhir-1");
        assertThat(canonical.tenantId()).isEqualTo("tenant-A");
        assertThat(canonical.resourceType()).isEqualTo(CanonicalResourceType.OBSERVATION);
        assertThat(canonical.sourceSystem()).isEqualTo("FHIR_R4");
        assertThat(canonical.sourceRecordId()).isEqualTo("Observation/obs-1");
        assertThat(canonical.mappedVersion()).isEqualTo("FHIR_R4:Observation");
        assertThat(canonical.eventTime()).isEqualTo(Instant.parse("2026-06-03T00:00:00Z"));
        assertThat(canonical.receivedTime()).isEqualTo(Instant.parse("2026-06-03T00:00:10Z"));
        assertThat(canonical.qualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(canonical.seqNo()).isEqualTo(2);
        assertThat(canonical.traceId()).isEqualTo("trace-fhir");

        JsonNode payload = json.readTree(canonical.resourcePayloadJson());
        assertThat(payload.path("observationId").asText()).isEqualTo("obs-1");
        assertThat(payload.path("code").asText()).isEqualTo("HB");
        assertThat(payload.path("displayName").asText()).isEqualTo("血红蛋白");
        assertThat(payload.path("valueNumeric").decimalValue()).isEqualByComparingTo("128");
        assertThat(payload.path("unit").asText()).isEqualTo("g/L");
        assertThat(payload.path("sourceSystem").asText()).isEqualTo("FHIR_R4");
        assertThat(payload.path("sourceRecordId").asText()).isEqualTo("Observation/obs-1");
        assertThat(payload.path("mappedVersion").asText()).isEqualTo("FHIR_R4:Observation");
        assertThat(payload.path("qualityStatus").asText()).isEqualTo("PARTIAL");

        assertThat(result.mappingRate()).isEqualByComparingTo(new BigDecimal("0.9000"));
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo("warning");
            assertThat(issue.code()).isEqualTo("not-supported");
            assertThat(issue.diagnostics()).contains("TERM-01").contains("urn:local:lis");
        });
    }

    @Test
    void mapsR4ObservationThroughTerminologyPortWhenConfirmed() throws Exception {
        FhirR4CanonicalMapper confirmedMapper = new FhirR4CanonicalMapper(json, terminologyReturning("VALID"));
        JsonNode observation = json.readTree("""
            {
              "resourceType": "Observation",
              "id": "obs-confirmed",
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

        CanonicalResourceMappingResult result = confirmedMapper.fromR4(new FhirCanonicalMappingRequest(
            "tenant-A",
            "snapshot-fhir-2",
            3,
            "trace-fhir-confirmed",
            Instant.parse("2026-06-03T00:00:10Z"),
            observation));

        assertThat(result.issues()).isEmpty();
        assertThat(result.mappingRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(result.resource().qualityStatus()).isEqualTo(QualityStatus.VALID);
        JsonNode payload = json.readTree(result.resource().resourcePayloadJson());
        assertThat(payload.path("sourceSystem").asText()).isEqualTo("FHIR_R4");
        assertThat(payload.path("mappedVersion").asText()).isEqualTo("FHIR_R4:Observation");
    }

    private static TerminologyMappingPort terminologyReturning(String status) {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }
}
