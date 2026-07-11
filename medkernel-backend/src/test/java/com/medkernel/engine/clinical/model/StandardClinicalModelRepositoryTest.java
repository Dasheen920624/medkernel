package com.medkernel.engine.clinical.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * SYS-01 标准临床模型关系库权威表往返测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
    ClinicalPatientIdCallback.class,
    ClinicalEncounterIdCallback.class,
    ClinicalConditionIdCallback.class,
    ClinicalObservationIdCallback.class,
    ClinicalMedicationIdCallback.class,
    ClinicalProcedureIdCallback.class,
    ClinicalDiagnosticReportIdCallback.class,
    ClinicalDocumentIdCallback.class,
    ClinicalNursingAssessmentIdCallback.class,
    ClinicalCarePlanIdCallback.class,
    ClinicalFollowUpIdCallback.class,
    ClinicalClaimIdCallback.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:standard-clinical-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class StandardClinicalModelRepositoryTest {

    @Autowired ClinicalPatientRepository patients;
    @Autowired ClinicalEncounterRepository encounters;
    @Autowired ClinicalConditionRepository conditions;
    @Autowired ClinicalObservationRepository observations;
    @Autowired ClinicalMedicationRepository medications;
    @Autowired ClinicalProcedureRepository procedures;
    @Autowired ClinicalDiagnosticReportRepository diagnosticReports;
    @Autowired ClinicalDocumentRepository documents;
    @Autowired ClinicalNursingAssessmentRepository nursingAssessments;
    @Autowired ClinicalCarePlanRepository carePlans;
    @Autowired ClinicalFollowUpRepository followUps;
    @Autowired ClinicalClaimRepository claims;

    @Test
    void persistsAndReadsTwelveObjectsWithinTenant() {
        ClinicalPatient patient = patients.save(patient("tenant-A", null, "SRC-1"));
        encounters.save(encounter("tenant-A", null, patient.patientId()));
        conditions.save(condition("tenant-A", null, patient.patientId()));
        observations.save(observation("tenant-A", null, patient.patientId()));
        medications.save(medication("tenant-A", null, patient.patientId()));
        procedures.save(procedure("tenant-A", null, patient.patientId()));
        diagnosticReports.save(report("tenant-A", null, patient.patientId()));
        documents.save(document("tenant-A", null, patient.patientId()));
        nursingAssessments.save(nursingAssessment("tenant-A", null, patient.patientId()));
        carePlans.save(carePlan("tenant-A", null, patient.patientId()));
        followUps.save(followUp("tenant-A", null, patient.patientId()));
        claims.save(claim("tenant-A", null, patient.patientId()));

        assertThat(patient.patientId()).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(patients.findByTenantIdAndPatientId("tenant-A", patient.patientId())).contains(patient);
        assertThat(encounters.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(conditions.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(observations.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(medications.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(procedures.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(diagnosticReports.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(documents.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(nursingAssessments.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(carePlans.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(followUps.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(claims.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
    }

    @Test
    void patientLookupNeverCrossesTenant() {
        ClinicalPatient tenantAPatient = patients.save(patient("tenant-A", null, "SRC-1"));
        patients.save(patient("tenant-B", null, "SRC-1"));

        Optional<ClinicalPatient> tenantA = patients.findByTenantIdAndSourceSystemAndSourceId(
            "tenant-A", "HIS", "SRC-1");

        assertThat(tenantA).isPresent();
        assertThat(tenantA.get().tenantId()).isEqualTo("tenant-A");
        assertThat(patients.findByTenantIdAndPatientId("tenant-B", tenantAPatient.patientId())).isEmpty();
    }

    @Test
    void claimInsertCreatesAssignedBusinessIdInsteadOfUpdating() {
        String patientId = patients.save(patient("tenant-A", null, "SRC-CLAIM-PATIENT")).patientId();
        ClinicalClaim frontdeskClaim = claim("tenant-A", "claim-frontdesk-assigned", patientId);

        claims.insert(frontdeskClaim);

        assertThat(claims.findByTenantIdAndPatientId("tenant-A", patientId))
            .extracting(ClinicalClaim::claimId)
            .containsExactly("claim-frontdesk-assigned");
    }

    private ClinicalPatient patient(String tenantId, String patientId, String sourceId) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new ClinicalPatient(patientId, tenantId, "/platform/group/hospital/dept", "HIS", sourceId,
            "Patient/" + patientId, "cipher-name-" + patientId, "张*", "cipher-id-" + patientId, "3301********1234",
            "cipher-phone-" + patientId, "138****0000", LocalDate.of(1980, 1, 1), "M", now, "tester", now,
            "tester", "trace-" + patientId);
    }

    private ClinicalEncounter encounter(String tenantId, String encounterId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:01:00Z");
        return new ClinicalEncounter(encounterId, tenantId, "/platform/group/hospital/dept", "HIS", "SRC-" + encounterId,
            "Encounter/" + encounterId, patientId, "IP", "IN_PROGRESS", now, null, "dept-a", now, "tester", now,
            "tester", "trace-" + encounterId);
    }

    private ClinicalCondition condition(String tenantId, String conditionId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:02:00Z");
        return new ClinicalCondition(conditionId, tenantId, "/platform/group/hospital/dept", "EMR", "SRC-" + conditionId,
            "Condition/" + conditionId, patientId, null, "A00", "ICD-10", "标准诊断", "ACTIVE", now, "tester", now,
            "tester", "trace-" + conditionId);
    }

    private ClinicalObservation observation(String tenantId, String observationId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:03:00Z");
        return new ClinicalObservation(observationId, tenantId, "/platform/group/hospital/dept", "LIS", "SRC-" + observationId,
            "Observation/" + observationId, patientId, null, "OBS-1", "LOINC", "标准观察", new BigDecimal("3.2"),
            "mmol/L", null, now, "tester", now, "tester", "trace-" + observationId);
    }

    private ClinicalMedication medication(String tenantId, String medicationId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:04:00Z");
        return new ClinicalMedication(medicationId, tenantId, "/platform/group/hospital/dept", "HIS", "SRC-" + medicationId,
            "MedicationRequest/" + medicationId, patientId, null, "MED-1", "DRUG", "标准药品", new BigDecimal("1"),
            "片", "口服", "BID", "ACTIVE", now, "tester", now, "tester", "trace-" + medicationId);
    }

    private ClinicalProcedure procedure(String tenantId, String procedureId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:05:00Z");
        return new ClinicalProcedure(procedureId, tenantId, "/platform/group/hospital/dept", "EMR", "SRC-" + procedureId,
            "Procedure/" + procedureId, patientId, null, "PROC-1", "ICD-9-CM-3", "标准操作", "COMPLETED", now,
            "tester", now, "tester", "trace-" + procedureId);
    }

    private ClinicalDiagnosticReport report(String tenantId, String reportId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:06:00Z");
        return new ClinicalDiagnosticReport(reportId, tenantId, "/platform/group/hospital/dept", "LIS", "SRC-" + reportId,
            "DiagnosticReport/" + reportId, patientId, null, "LAB", "FINAL", "报告结论摘要", now, "tester", now,
            "tester", "trace-" + reportId);
    }

    private ClinicalDocument document(String tenantId, String documentId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:07:00Z");
        return new ClinicalDocument(documentId, tenantId, "/platform/group/hospital/dept", "EMR", "SRC-" + documentId,
            "DocumentReference/" + documentId, patientId, null, "DISCHARGE_SUMMARY", "CURRENT", "sha256-content",
            now, "tester", now, "tester", "trace-" + documentId);
    }

    private ClinicalNursingAssessment nursingAssessment(String tenantId, String assessmentId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:08:00Z");
        return new ClinicalNursingAssessment(assessmentId, tenantId, "/platform/group/hospital/dept", "NIS",
            "SRC-" + assessmentId, "Observation/" + assessmentId, patientId, null, "NURSING_RISK", "RECORDED",
            "LOW", now, "tester", now, "tester", "trace-" + assessmentId);
    }

    private ClinicalCarePlan carePlan(String tenantId, String carePlanId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:09:00Z");
        return new ClinicalCarePlan(carePlanId, tenantId, "/platform/group/hospital/dept", "PATHWAY", "SRC-" + carePlanId,
            "CarePlan/" + carePlanId, patientId, null, "PATHWAY-1", "ACTIVE", now, "tester", now, "tester",
            "trace-" + carePlanId);
    }

    private ClinicalFollowUp followUp(String tenantId, String followUpId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:10:00Z");
        return new ClinicalFollowUp(followUpId, tenantId, "/platform/group/hospital/dept", "FOLLOWUP", "SRC-" + followUpId,
            "Task/" + followUpId, patientId, null, "POST_DISCHARGE", "PLANNED", now.plusSeconds(86_400), now, "tester",
            now, "tester", "trace-" + followUpId);
    }

    private ClinicalClaim claim(String tenantId, String claimId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:11:00Z");
        return new ClinicalClaim(claimId, tenantId, "/platform/group/hospital/dept", "INSURANCE", "SRC-" + claimId,
            "Claim/" + claimId, patientId, null, "DRG", "SUBMITTED", new BigDecimal("1200.00"), now, "tester", now,
            "tester", "trace-" + claimId);
    }
}
