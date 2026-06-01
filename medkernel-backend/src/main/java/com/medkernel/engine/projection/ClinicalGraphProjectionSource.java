package com.medkernel.engine.projection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

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

/**
 * 从关系库标准临床对象生成可重放的临床图投影事实。
 */
@Component
public class ClinicalGraphProjectionSource {

    private static final String HAS_RESOURCE = "HAS_RESOURCE";

    private final ClinicalPatientRepository patients;
    private final ClinicalEncounterRepository encounters;
    private final ClinicalConditionRepository conditions;
    private final ClinicalObservationRepository observations;
    private final ClinicalMedicationRepository medications;
    private final ClinicalProcedureRepository procedures;
    private final ClinicalDiagnosticReportRepository diagnosticReports;
    private final ClinicalDocumentRepository documents;
    private final ClinicalNursingAssessmentRepository nursingAssessments;
    private final ClinicalCarePlanRepository carePlans;
    private final ClinicalFollowUpRepository followUps;
    private final ClinicalClaimRepository claims;

    public ClinicalGraphProjectionSource(
            ClinicalPatientRepository patients,
            ClinicalEncounterRepository encounters,
            ClinicalConditionRepository conditions,
            ClinicalObservationRepository observations,
            ClinicalMedicationRepository medications,
            ClinicalProcedureRepository procedures,
            ClinicalDiagnosticReportRepository diagnosticReports,
            ClinicalDocumentRepository documents,
            ClinicalNursingAssessmentRepository nursingAssessments,
            ClinicalCarePlanRepository carePlans,
            ClinicalFollowUpRepository followUps,
            ClinicalClaimRepository claims) {
        this.patients = patients;
        this.encounters = encounters;
        this.conditions = conditions;
        this.observations = observations;
        this.medications = medications;
        this.procedures = procedures;
        this.diagnosticReports = diagnosticReports;
        this.documents = documents;
        this.nursingAssessments = nursingAssessments;
        this.carePlans = carePlans;
        this.followUps = followUps;
        this.claims = claims;
    }

    public List<ProjectionFact> factsForTenant(String tenantId) {
        List<ProjectionFact> facts = new ArrayList<>();
        patients.findByTenantId(tenantId).forEach(patient -> facts.add(patientNode(patient)));
        encounters.findByTenantId(tenantId).forEach(encounter -> {
            facts.add(node("ENCOUNTER", encounter.encounterId(), encounter.patientId(), null,
                encounter.fhirResourceId(), encounter.sourceSystem(), encounter.sourceId(), null, null,
                encounter.status(), encounter.updatedAt()));
            addPatientEdge(facts, encounter.patientId(), "ENCOUNTER", encounter.encounterId(), encounter.updatedAt());
        });
        conditions.findByTenantId(tenantId).forEach(condition -> addClinicalResource(facts, "CONDITION",
            condition.conditionId(), condition.patientId(), condition.encounterId(), condition.fhirResourceId(),
            condition.sourceSystem(), condition.sourceId(), condition.code(), condition.codeSystem(),
            condition.clinicalStatus(), condition.updatedAt()));
        observations.findByTenantId(tenantId).forEach(observation -> addClinicalResource(facts, "OBSERVATION",
            observation.observationId(), observation.patientId(), observation.encounterId(), observation.fhirResourceId(),
            observation.sourceSystem(), observation.sourceId(), observation.code(), observation.codeSystem(),
            observation.criticalFlag(), observation.updatedAt()));
        medications.findByTenantId(tenantId).forEach(medication -> addClinicalResource(facts, "MEDICATION",
            medication.medicationId(), medication.patientId(), medication.encounterId(), medication.fhirResourceId(),
            medication.sourceSystem(), medication.sourceId(), medication.code(), medication.codeSystem(),
            medication.status(), medication.updatedAt()));
        procedures.findByTenantId(tenantId).forEach(procedure -> addClinicalResource(facts, "PROCEDURE",
            procedure.procedureId(), procedure.patientId(), procedure.encounterId(), procedure.fhirResourceId(),
            procedure.sourceSystem(), procedure.sourceId(), procedure.code(), procedure.codeSystem(),
            procedure.status(), procedure.updatedAt()));
        diagnosticReports.findByTenantId(tenantId).forEach(report -> addClinicalResource(facts, "DIAGNOSTIC_REPORT",
            report.reportId(), report.patientId(), report.encounterId(), report.fhirResourceId(),
            report.sourceSystem(), report.sourceId(), report.reportType(), null, report.status(), report.updatedAt()));
        documents.findByTenantId(tenantId).forEach(document -> addClinicalResource(facts, "DOCUMENT",
            document.documentId(), document.patientId(), document.encounterId(), document.fhirResourceId(),
            document.sourceSystem(), document.sourceId(), document.documentType(), null, document.status(),
            document.updatedAt()));
        nursingAssessments.findByTenantId(tenantId).forEach(assessment -> addClinicalResource(facts,
            "NURSING_ASSESSMENT", assessment.assessmentId(), assessment.patientId(), assessment.encounterId(),
            assessment.fhirResourceId(), assessment.sourceSystem(), assessment.sourceId(), assessment.assessmentType(),
            null, assessment.status(), assessment.updatedAt()));
        carePlans.findByTenantId(tenantId).forEach(carePlan -> addClinicalResource(facts, "CARE_PLAN",
            carePlan.carePlanId(), carePlan.patientId(), carePlan.encounterId(), carePlan.fhirResourceId(),
            carePlan.sourceSystem(), carePlan.sourceId(), carePlan.pathwayId(), null, carePlan.status(),
            carePlan.updatedAt()));
        followUps.findByTenantId(tenantId).forEach(followUp -> addClinicalResource(facts, "FOLLOW_UP",
            followUp.followUpId(), followUp.patientId(), followUp.encounterId(), followUp.fhirResourceId(),
            followUp.sourceSystem(), followUp.sourceId(), followUp.planType(), null, followUp.status(),
            followUp.updatedAt()));
        claims.findByTenantId(tenantId).forEach(claim -> addClinicalResource(facts, "CLAIM",
            claim.claimId(), claim.patientId(), claim.encounterId(), claim.fhirResourceId(), claim.sourceSystem(),
            claim.sourceId(), claim.claimType(), null, claim.status(), claim.updatedAt()));
        return List.copyOf(facts);
    }

    private ProjectionFact patientNode(ClinicalPatient patient) {
        String payload = payload(
            "tenantId", patient.tenantId(),
            "type", "PATIENT",
            "id", patient.patientId(),
            "orgPath", patient.orgPath(),
            "sourceSystem", patient.sourceSystem(),
            "sourceId", patient.sourceId(),
            "fhirResourceId", patient.fhirResourceId(),
            "genderCode", patient.genderCode(),
            "birthDate", value(patient.birthDate()),
            "updatedAt", value(patient.updatedAt()));
        return ProjectionFact.node("PATIENT", patient.patientId(), payload, patient.updatedAt());
    }

    private ProjectionFact node(String type, String id, String patientId, String encounterId, String fhirResourceId,
            String sourceSystem, String sourceId, String code, String codeSystem, String status, Instant updatedAt) {
        String payload = payload(
            "type", type,
            "id", id,
            "patientId", patientId,
            "encounterId", encounterId,
            "sourceSystem", sourceSystem,
            "sourceId", sourceId,
            "fhirResourceId", fhirResourceId,
            "code", code,
            "codeSystem", codeSystem,
            "status", status,
            "updatedAt", value(updatedAt));
        return ProjectionFact.node(type, id, payload, updatedAt);
    }

    private void addClinicalResource(List<ProjectionFact> facts, String type, String id, String patientId,
            String encounterId, String fhirResourceId, String sourceSystem, String sourceId, String code,
            String codeSystem, String status, Instant updatedAt) {
        facts.add(node(type, id, patientId, encounterId, fhirResourceId, sourceSystem, sourceId, code, codeSystem,
            status, updatedAt));
        addPatientEdge(facts, patientId, type, id, updatedAt);
        if (encounterId != null && !encounterId.isBlank()) {
            addEdge(facts, "ENCOUNTER:" + encounterId, HAS_RESOURCE, type + ":" + id, updatedAt);
        }
    }

    private void addPatientEdge(List<ProjectionFact> facts, String patientId, String type, String id,
            Instant updatedAt) {
        if (patientId != null && !patientId.isBlank()) {
            addEdge(facts, "PATIENT:" + patientId, HAS_RESOURCE, type + ":" + id, updatedAt);
        }
    }

    private void addEdge(List<ProjectionFact> facts, String subjectKey, String predicate, String objectKey,
            Instant updatedAt) {
        facts.add(ProjectionFact.edge(subjectKey, predicate, objectKey, payload(
            "from", subjectKey,
            "predicate", predicate,
            "to", objectKey,
            "updatedAt", value(updatedAt)), updatedAt));
    }

    private String payload(String... pairs) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parts.add(pairs[i] + "=" + sanitize(pairs[i + 1]));
        }
        return String.join("|", parts);
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
