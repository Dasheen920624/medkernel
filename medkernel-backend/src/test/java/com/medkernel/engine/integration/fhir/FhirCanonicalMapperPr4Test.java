package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.TerminologyMappingPort;
import org.junit.jupiter.api.Test;

class FhirCanonicalMapperPr4Test {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final FhirR4CanonicalMapper r4 = new FhirR4CanonicalMapper(json, terminologyReturning("VALID"));
    private final FhirR5CanonicalMapper r5 = new FhirR5CanonicalMapper(json, terminologyReturning("VALID"));

    @Test
    void mapsTenCoreCanonicalResourcesToR4AndR5FhirResourcesWithoutInventingMissingLinks() throws Exception {
        Map<CanonicalResourceType, String> expectedFhirTypes = Map.ofEntries(
            Map.entry(CanonicalResourceType.PATIENT, "Patient"),
            Map.entry(CanonicalResourceType.ENCOUNTER, "Encounter"),
            Map.entry(CanonicalResourceType.CONDITION, "Condition"),
            Map.entry(CanonicalResourceType.ALLERGY_INTOLERANCE, "AllergyIntolerance"),
            Map.entry(CanonicalResourceType.OBSERVATION, "Observation"),
            Map.entry(CanonicalResourceType.MEDICATION, "Medication"),
            Map.entry(CanonicalResourceType.PROCEDURE, "Procedure"),
            Map.entry(CanonicalResourceType.CARE_PLAN, "CarePlan"),
            Map.entry(CanonicalResourceType.DIAGNOSTIC_REPORT, "DiagnosticReport"),
            Map.entry(CanonicalResourceType.DOCUMENT, "DocumentReference")
        );

        for (Map.Entry<CanonicalResourceType, String> entry : expectedFhirTypes.entrySet()) {
            CanonicalResource canonical = canonical(entry.getKey());

            JsonNode r4Resource = r4.toR4(canonical).resource();
            JsonNode r5Resource = r5.toR5(canonical).resource();

            assertThat(r4Resource.path("resourceType").asText()).isEqualTo(entry.getValue());
            assertThat(r5Resource.path("resourceType").asText()).isEqualTo(entry.getValue());
            assertThat(r4Resource.path("id").asText()).isEqualTo(canonical.resourceId());
            assertThat(r5Resource.path("id").asText()).isEqualTo(canonical.resourceId());
            assertThat(r4Resource.path("meta").path("source").asText())
                .isEqualTo("canonical_resource/" + canonical.resourceId());
            assertThat(r5Resource.path("meta").path("source").asText())
                .isEqualTo("canonical_resource/" + canonical.resourceId());
            assertThat(r4Resource.path("subject").path("reference").isMissingNode())
                .as("CanonicalResource 没有患者关联字段时，FHIR 出站不得伪造 subject")
                .isTrue();
        }
    }

    @Test
    void mapsUnconfirmedConditionCodeToPartialWarningWithoutApproximateFallback() throws Exception {
        FhirR4CanonicalMapper mapper = new FhirR4CanonicalMapper(json, terminologyReturning("UNKNOWN"));
        JsonNode condition = json.readTree("""
            {
              "resourceType": "Condition",
              "id": "cond-unmapped",
              "code": {
                "coding": [
                  {"system": "urn:local:diagnosis", "code": "LOCAL-J00", "display": "本地诊断"}
                ]
              },
              "onsetDateTime": "2026-06-03T00:00:00Z"
            }
            """);

        CanonicalResourceMappingResult result = mapper.fromR4(new FhirCanonicalMappingRequest(
            "tenant-A", "snapshot-pr4", 1, "trace-pr4",
            Instant.parse("2026-06-03T00:00:01Z"), condition));

        assertThat(result.resource().resourceType()).isEqualTo(CanonicalResourceType.CONDITION);
        assertThat(result.resource().qualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(result.mappingRate()).isEqualByComparingTo("0.9000");
        assertThat(result.issues())
            .extracting(FhirOperationOutcomeIssue::diagnostics)
            .anySatisfy(diagnostics -> assertThat(diagnostics).contains("TERM-01", "禁止字符近似兜底"));
    }

    private CanonicalResource canonical(CanonicalResourceType type) throws Exception {
        return new CanonicalResource(
            700L + type.ordinal(),
            "res-" + type.name().toLowerCase(),
            "snapshot-pr4",
            "tenant-A",
            type,
            payload(type),
            "HIS",
            "SRC-" + type.name(),
            "canonical-v1",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:01Z"),
            QualityStatus.VALID,
            type.ordinal(),
            "trace-pr4");
    }

    private String payload(CanonicalResourceType type) throws Exception {
        return switch (type) {
            case PATIENT -> """
                {
                  "mpi": "MPI-001",
                  "name": "患者一",
                  "birthDate": "1980-01-01",
                  "gender": "male",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "PAT-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case ENCOUNTER -> """
                {
                  "encounterId": "enc-1",
                  "encounterType": "inpatient",
                  "admissionTime": "2026-06-03T00:00:00Z",
                  "departmentId": "dept-A",
                  "attendingDoctorId": "doc-A",
                  "bedId": "bed-1",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "ENC-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case CONDITION -> """
                {
                  "conditionId": "cond-1",
                  "code": "J00",
                  "codeSystem": "http://hl7.org/fhir/sid/icd-10",
                  "displayName": "诊断显示",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "COND-001",
                  "mappedVersion": "canonical-v1",
                  "onsetTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case ALLERGY_INTOLERANCE -> """
                {
                  "allergyIntoleranceId": "alg-1",
                  "code": "PEN",
                  "codeSystem": "urn:local:drug",
                  "substance": "青霉素",
                  "category": "medication",
                  "criticality": "high",
                  "reactions": ["皮疹"],
                  "clinicalStatus": "active",
                  "verificationStatus": "confirmed",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "ALG-001",
                  "mappedVersion": "canonical-v1",
                  "onsetTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case OBSERVATION -> """
                {
                  "observationId": "obs-1",
                  "code": "718-7",
                  "displayName": "检验项目",
                  "valueNumeric": 128,
                  "unit": "g/L",
                  "sourceSystem": "LIS",
                  "sourceRecordId": "OBS-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case MEDICATION -> """
                {
                  "medicationId": "med-1",
                  "code": "MED-001",
                  "displayName": "药品条目",
                  "dose": 1,
                  "doseUnit": "片",
                  "route": "口服",
                  "frequency": "每日一次",
                  "prescriptionStatus": "active",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "MED-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case PROCEDURE -> """
                {
                  "procedureId": "proc-1",
                  "code": "PROC-001",
                  "displayName": "操作条目",
                  "surgeonId": "doc-A",
                  "performedAt": "2026-06-03T00:00:00Z",
                  "sourceSystem": "HIS",
                  "sourceRecordId": "PROC-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case CARE_PLAN -> """
                {
                  "planId": "care-1",
                  "pathwayId": "path-1",
                  "currentNodeId": "node-1",
                  "varianceCode": "none",
                  "sourceSystem": "PATH",
                  "sourceRecordId": "CARE-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case DIAGNOSTIC_REPORT -> """
                {
                  "reportId": "report-1",
                  "reportType": "exam",
                  "conclusion": "报告结论",
                  "keyFindings": ["关键发现"],
                  "signedBy": "doc-A",
                  "signedAt": "2026-06-03T00:00:00Z",
                  "sourceSystem": "RIS",
                  "sourceRecordId": "REPORT-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case DOCUMENT -> """
                {
                  "documentId": "doc-1",
                  "documentType": "record",
                  "contentDigest": "sha256:document-digest",
                  "signedBy": "doc-A",
                  "signedAt": "2026-06-03T00:00:00Z",
                  "sourceSystem": "EMR",
                  "sourceRecordId": "DOC-001",
                  "mappedVersion": "canonical-v1",
                  "eventTime": "2026-06-03T00:00:00Z",
                  "receivedTime": "2026-06-03T00:00:01Z",
                  "qualityStatus": "VALID"
                }
                """;
            case NURSING_ASSESSMENT, FOLLOW_UP, CLAIM -> throw new IllegalArgumentException("OPT-01 PR4 不映射该类型: " + type);
        };
    }

    private static TerminologyMappingPort terminologyReturning(String status) {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }
}
