package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.shared.context.OrgScope;

class ClinicalEventContextContractTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void contextCarriesStableClinicalEventFieldsForAllEngines() {
        var payload = json.createObjectNode()
            .put("diagnosisCode", "I10")
            .put("sourceRecordId", "his-rec-1");
        var orgScope = new OrgScope("tenant-A", "group-A", "hospital-A",
            "campus-A", "site-A", "dept-A", "specialty-A");
        var anchor = new ClinicalCodeMappingAnchor(
            CanonicalResourceType.CONDITION,
            "cond-1",
            "code",
            "I10",
            "ICD-10",
            "原发性高血压",
            "TERM.DIAGNOSIS",
            "HIS",
            "his-rec-1",
            null);

        var context = new ClinicalEventContext(
            "evt-1",
            "tenant-A",
            orgScope,
            ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW,
            "MPI-1",
            "ENC-1",
            ClinicalSetting.INPATIENT,
            "ctx-1",
            "HIS",
            "pkg-2026.06",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            "HIS:patient-view",
            "trace-1",
            ClinicalEventTestContexts.resources("MPI-1", "HIS", "pkg-2026.06",
                Instant.parse("2026-06-01T01:00:00Z")),
            payload,
            List.of(anchor));

        assertThat(context.eventId()).isEqualTo("evt-1");
        assertThat(context.tenantId()).isEqualTo("tenant-A");
        assertThat(context.orgScope().departmentId()).isEqualTo("dept-A");
        assertThat(context.patientId()).isEqualTo("MPI-1");
        assertThat(context.encounterId()).isEqualTo("ENC-1");
        assertThat(context.clinicalSetting()).isEqualTo(ClinicalSetting.INPATIENT);
        assertThat(context.contextSnapshotId()).isEqualTo("ctx-1");
        assertThat(context.triggerSource()).isEqualTo("HIS:patient-view");
        assertThat(context.triggerPoint()).isEqualTo("patient-view");
        assertThat(context.traceId()).isEqualTo("trace-1");
        assertThat(context.payloadDigest()).isEqualTo("sha256:payload");
        assertThat(context.resources().patient().mpi()).isEqualTo("MPI-1");
        assertThat(context.payload().path("diagnosisCode").asText()).isEqualTo("I10");
        assertThat(context.codeMappingAnchors()).containsExactly(anchor);
        assertThat(anchor.key()).isEqualTo("CONDITION:cond-1:code:I10");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clinicalEventProjectionCases")
    void factoryProjectsEveryClinicalEventTypeToCanonicalResourcesBeforeDispatch(ProjectionCase item)
            throws Exception {
        ClinicalEvent event = new ClinicalEvent(
            1L,
            "evt-" + item.eventType().name().toLowerCase(),
            "tenant-A",
            item.eventType(),
            item.triggerPoint(),
            null,
            null,
            "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
            "MPI-1",
            "ENC-1",
            ClinicalSetting.INPATIENT,
            item.sourceSystem(),
            "pkg-2026.06",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            Instant.parse("2026-06-01T01:00:01Z"),
            null,
            ClinicalEventStatus.MAPPED,
            null,
            null,
            0,
            null,
            "trace-1");
        ClinicalEventPayload payload = new ClinicalEventPayload(
            1L,
            event.eventId(),
            "tenant-A",
            item.payloadJson(),
            null,
            "INLINE",
            "application/json",
            "sha256:payload",
            (long) item.payloadJson().length(),
            Instant.parse("2026-06-01T01:00:01Z"),
            null);

        ClinicalEventContext context = new ClinicalEventContextFactory(json).from(event, payload);

        assertThat(context.triggerPoint()).isEqualTo(item.triggerPoint().wireValue());
        assertThat(context.payload().path(item.resourceArray()).path(0).path(item.fieldName()).asText())
            .isEqualTo(item.expectedValue());
        assertThat(context.payload().has("eventPayload")).isTrue();
        assertThat(context.codeMappingAnchors())
            .as("每类事件都要在求值前产出可追溯编码锚点")
            .isNotEmpty();
    }

    private static Stream<ProjectionCase> clinicalEventProjectionCases() {
        return Stream.of(
            new ProjectionCase(
                ClinicalEventType.ORDER,
                ClinicalEventTriggerPoint.ORDER_SIGN,
                "HIS",
                "medications",
                "code",
                "ATC-J01CA04",
                """
                    {
                      "orders": [
                        {"orderId": "ord-1", "localCode": "HIS-AMOX", "standardCode": "ATC-J01CA04", "displayName": "阿莫西林"}
                      ]
                    }
                    """),
            new ProjectionCase(
                ClinicalEventType.ADMISSION,
                ClinicalEventTriggerPoint.PATIENT_VIEW,
                "HIS",
                "encounters",
                "encounterType",
                "INPATIENT",
                """
                    {
                      "admission": {"encounterType": "INPATIENT", "departmentId": "DEPT-A"}
                    }
                    """),
            new ProjectionCase(
                ClinicalEventType.DIAGNOSIS,
                ClinicalEventTriggerPoint.PATIENT_VIEW,
                "EMR",
                "conditions",
                "code",
                "I21.0",
                """
                    {
                      "diagnoses": [
                        {"conditionId": "cond-1", "localCode": "DX-AMI", "standardCode": "I21.0", "codeSystem": "ICD-10", "displayName": "急性心肌梗死"}
                      ]
                    }
                    """),
            new ProjectionCase(
                ClinicalEventType.REPORT,
                ClinicalEventTriggerPoint.RESULT_REVIEW,
                "LIS",
                "observations",
                "code",
                "GLU",
                """
                    {
                      "report": {"reportId": "rep-1", "reportType": "LAB", "conclusion": "已审核"},
                      "results": [
                        {"observationId": "obs-1", "localCode": "LIS-GLU", "standardCode": "GLU", "displayName": "血糖", "valueNumeric": 12.3, "unit": "mmol/L"}
                      ]
                    }
                    """),
            new ProjectionCase(
                ClinicalEventType.FOLLOWUP,
                ClinicalEventTriggerPoint.FOLLOWUP_ALERT,
                "FOLLOWUP",
                "followUps",
                "planType",
                "PHONE",
                """
                    {
                      "followup": {"followUpId": "fu-1", "planType": "PHONE", "questionnaireId": "Q-CKD", "abnormalFlag": "ABNORMAL"}
                    }
                    """),
            new ProjectionCase(
                ClinicalEventType.DISCHARGE,
                ClinicalEventTriggerPoint.DISCHARGE_SIGN,
                "EMR",
                "documents",
                "documentType",
                "DISCHARGE_SUMMARY",
                """
                    {
                      "dischargeSummary": {"documentId": "doc-1", "documentType": "DISCHARGE_SUMMARY", "contentDigest": "sha256:doc"}
                    }
                    """)
        );
    }

    private record ProjectionCase(
        ClinicalEventType eventType,
        ClinicalEventTriggerPoint triggerPoint,
        String sourceSystem,
        String resourceArray,
        String fieldName,
        String expectedValue,
        String payloadJson
    ) {
        @Override
        public String toString() {
            return eventType.name();
        }
    }
}
