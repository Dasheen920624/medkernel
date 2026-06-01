package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.clinical.model.ClinicalCarePlan;
import com.medkernel.engine.clinical.model.ClinicalCarePlanRepository;
import com.medkernel.engine.clinical.model.ClinicalClaim;
import com.medkernel.engine.clinical.model.ClinicalClaimRepository;
import com.medkernel.engine.clinical.model.ClinicalCondition;
import com.medkernel.engine.clinical.model.ClinicalConditionRepository;
import com.medkernel.engine.clinical.model.ClinicalDiagnosticReport;
import com.medkernel.engine.clinical.model.ClinicalDiagnosticReportRepository;
import com.medkernel.engine.clinical.model.ClinicalDocument;
import com.medkernel.engine.clinical.model.ClinicalDocumentRepository;
import com.medkernel.engine.clinical.model.ClinicalEncounter;
import com.medkernel.engine.clinical.model.ClinicalEncounterRepository;
import com.medkernel.engine.clinical.model.ClinicalFollowUp;
import com.medkernel.engine.clinical.model.ClinicalFollowUpRepository;
import com.medkernel.engine.clinical.model.ClinicalMedication;
import com.medkernel.engine.clinical.model.ClinicalMedicationRepository;
import com.medkernel.engine.clinical.model.ClinicalNursingAssessment;
import com.medkernel.engine.clinical.model.ClinicalNursingAssessmentRepository;
import com.medkernel.engine.clinical.model.ClinicalObservation;
import com.medkernel.engine.clinical.model.ClinicalObservationRepository;
import com.medkernel.engine.clinical.model.ClinicalPatient;
import com.medkernel.engine.clinical.model.ClinicalPatientRepository;
import com.medkernel.engine.clinical.model.ClinicalProcedure;
import com.medkernel.engine.clinical.model.ClinicalProcedureRepository;

class ClinicalGraphProjectionSourceTest {

    private final ClinicalPatientRepository patients = mock(ClinicalPatientRepository.class);
    private final ClinicalEncounterRepository encounters = mock(ClinicalEncounterRepository.class);
    private final ClinicalConditionRepository conditions = mock(ClinicalConditionRepository.class);
    private final ClinicalObservationRepository observations = mock(ClinicalObservationRepository.class);
    private final ClinicalMedicationRepository medications = mock(ClinicalMedicationRepository.class);
    private final ClinicalProcedureRepository procedures = mock(ClinicalProcedureRepository.class);
    private final ClinicalDiagnosticReportRepository diagnosticReports = mock(ClinicalDiagnosticReportRepository.class);
    private final ClinicalDocumentRepository documents = mock(ClinicalDocumentRepository.class);
    private final ClinicalNursingAssessmentRepository nursingAssessments = mock(ClinicalNursingAssessmentRepository.class);
    private final ClinicalCarePlanRepository carePlans = mock(ClinicalCarePlanRepository.class);
    private final ClinicalFollowUpRepository followUps = mock(ClinicalFollowUpRepository.class);
    private final ClinicalClaimRepository claims = mock(ClinicalClaimRepository.class);

    private final ClinicalGraphProjectionSource source = new ClinicalGraphProjectionSource(
        patients, encounters, conditions, observations, medications, procedures, diagnosticReports, documents,
        nursingAssessments, carePlans, followUps, claims);

    @Test
    void createsGraphFactsFromRelationalClinicalAuthorityWithoutSensitiveFields() {
        seedRelationalClinicalObjects();

        List<ProjectionFact> facts = source.factsForTenant("tenant-A");

        assertThat(facts).extracting(ProjectionFact::factKey)
            .contains(
                "NODE:PATIENT:pat-1",
                "NODE:OBSERVATION:obs-1",
                "EDGE:PATIENT:pat-1:HAS_RESOURCE:OBSERVATION:obs-1",
                "EDGE:ENCOUNTER:enc-1:HAS_RESOURCE:OBSERVATION:obs-1");
        assertThat(facts).extracting(ProjectionFact::canonicalPayload)
            .noneMatch(payload -> payload.contains("cipher-name"))
            .noneMatch(payload -> payload.contains("cipher-id"))
            .noneMatch(payload -> payload.contains("cipher-phone"));
        assertThat(facts).allSatisfy(fact -> {
            assertThat(fact.targetType()).isEqualTo(ProjectionTargetType.CLINICAL_GRAPH);
            assertThat(fact.contentHash()).hasSize(64);
        });
    }

    private void seedRelationalClinicalObjects() {
        when(patients.findByTenantId("tenant-A")).thenReturn(List.of(patient()));
        when(patients.findByTenantIdAndPatientId("tenant-A", "pat-1")).thenReturn(Optional.of(patient()));
        when(encounters.findByTenantId("tenant-A")).thenReturn(List.of(encounter()));
        when(conditions.findByTenantId("tenant-A")).thenReturn(List.of(condition()));
        when(observations.findByTenantId("tenant-A")).thenReturn(List.of(observation()));
        when(medications.findByTenantId("tenant-A")).thenReturn(List.of(medication()));
        when(procedures.findByTenantId("tenant-A")).thenReturn(List.of(procedure()));
        when(diagnosticReports.findByTenantId("tenant-A")).thenReturn(List.of(report()));
        when(documents.findByTenantId("tenant-A")).thenReturn(List.of(document()));
        when(nursingAssessments.findByTenantId("tenant-A")).thenReturn(List.of(nursingAssessment()));
        when(carePlans.findByTenantId("tenant-A")).thenReturn(List.of(carePlan()));
        when(followUps.findByTenantId("tenant-A")).thenReturn(List.of(followUp()));
        when(claims.findByTenantId("tenant-A")).thenReturn(List.of(claim()));
    }

    private Instant now() {
        return Instant.parse("2026-06-01T00:00:00Z");
    }

    private ClinicalPatient patient() {
        return new ClinicalPatient("pat-1", "tenant-A", "/platform/group/hospital/dept", "HIS", "SRC-PAT",
            "Patient/fhir-pat-1", "cipher-name", "张*", "cipher-id", "3301********1234",
            "cipher-phone", "138****0000", LocalDate.of(1980, 1, 1), "M", now(), "tester", now(), "tester",
            "trace-1");
    }

    private ClinicalEncounter encounter() {
        return new ClinicalEncounter("enc-1", "tenant-A", "/platform/group/hospital/dept", "HIS", "SRC-ENC",
            "Encounter/fhir-enc-1", "pat-1", "IP", "IN_PROGRESS", now(), null, "dept-a", now(), "tester", now(),
            "tester", "trace-1");
    }

    private ClinicalCondition condition() {
        return new ClinicalCondition("cond-1", "tenant-A", "/platform/group/hospital/dept", "EMR", "SRC-COND",
            "Condition/fhir-cond-1", "pat-1", "enc-1", "A00", "ICD-10", "标准诊断", "ACTIVE", now(), "tester",
            now(), "tester", "trace-1");
    }

    private ClinicalObservation observation() {
        return new ClinicalObservation("obs-1", "tenant-A", "/platform/group/hospital/dept", "LIS", "SRC-OBS",
            "Observation/fhir-obs-1", "pat-1", "enc-1", "OBS-1", "LOINC", "标准观察", new BigDecimal("3.2"),
            "mmol/L", null, now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalMedication medication() {
        return new ClinicalMedication("med-1", "tenant-A", "/platform/group/hospital/dept", "HIS", "SRC-MED",
            "MedicationRequest/fhir-med-1", "pat-1", "enc-1", "MED-1", "DRUG", "标准药品", BigDecimal.ONE,
            "片", "口服", "BID", "ACTIVE", now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalProcedure procedure() {
        return new ClinicalProcedure("proc-1", "tenant-A", "/platform/group/hospital/dept", "EMR", "SRC-PROC",
            "Procedure/fhir-proc-1", "pat-1", "enc-1", "PROC-1", "ICD-9-CM-3", "标准操作", "COMPLETED",
            now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalDiagnosticReport report() {
        return new ClinicalDiagnosticReport("report-1", "tenant-A", "/platform/group/hospital/dept", "LIS",
            "SRC-REPORT", "DiagnosticReport/fhir-report-1", "pat-1", "enc-1", "LAB", "FINAL", "报告结论摘要",
            now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalDocument document() {
        return new ClinicalDocument("doc-1", "tenant-A", "/platform/group/hospital/dept", "EMR", "SRC-DOC",
            "DocumentReference/fhir-doc-1", "pat-1", "enc-1", "DISCHARGE_SUMMARY", "CURRENT", "sha256-content",
            now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalNursingAssessment nursingAssessment() {
        return new ClinicalNursingAssessment("nurse-1", "tenant-A", "/platform/group/hospital/dept", "NIS",
            "SRC-NURSE", "Observation/fhir-nurse-1", "pat-1", "enc-1", "NURSING_RISK", "RECORDED",
            "LOW", now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalCarePlan carePlan() {
        return new ClinicalCarePlan("care-1", "tenant-A", "/platform/group/hospital/dept", "PATHWAY",
            "SRC-CARE", "CarePlan/fhir-care-1", "pat-1", "enc-1", "PATHWAY-1", "ACTIVE", now(), "tester",
            now(), "tester", "trace-1");
    }

    private ClinicalFollowUp followUp() {
        return new ClinicalFollowUp("follow-1", "tenant-A", "/platform/group/hospital/dept", "FOLLOWUP",
            "SRC-FOLLOW", "Task/fhir-follow-1", "pat-1", "enc-1", "POST_DISCHARGE", "PLANNED",
            now().plusSeconds(86_400), now(), "tester", now(), "tester", "trace-1");
    }

    private ClinicalClaim claim() {
        return new ClinicalClaim("claim-1", "tenant-A", "/platform/group/hospital/dept", "INSURANCE",
            "SRC-CLAIM", "Claim/fhir-claim-1", "pat-1", "enc-1", "DRG", "SUBMITTED", new BigDecimal("1200.00"),
            now(), "tester", now(), "tester", "trace-1");
    }
}
