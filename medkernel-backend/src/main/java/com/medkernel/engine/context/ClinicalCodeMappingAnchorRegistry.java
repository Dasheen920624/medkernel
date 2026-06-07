package com.medkernel.engine.context;

import java.util.ArrayList;
import java.util.List;

import com.medkernel.engine.context.canonical.CanonicalAllergyIntolerance;
import com.medkernel.engine.context.canonical.CanonicalCarePlan;
import com.medkernel.engine.context.canonical.CanonicalClaim;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.context.canonical.CanonicalDocument;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalFollowUp;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalNursingAssessment;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.context.canonical.CanonicalProcedure;

/**
 * SYS-01 12 类标准对象编码字段锚点注册表。
 */
public final class ClinicalCodeMappingAnchorRegistry {

    private static final String TERM_DIAGNOSIS = "TERM.DIAGNOSIS";
    private static final String TERM_PROCEDURE = "TERM.PROCEDURE";
    private static final String TERM_DRUG = "TERM.DRUG";
    private static final String TERM_LAB = "TERM.LAB";
    private static final String TERM_EXAM = "TERM.EXAM";
    private static final String TERM_INSURANCE = "TERM.INSURANCE";
    private static final String TERM_DEPARTMENT = "TERM.DEPARTMENT";
    private static final String TERM_DOCUMENT = "TERM.DOCUMENT";
    private static final String TERM_FOLLOWUP = "TERM.FOLLOWUP";
    private static final String TERM_OTHER = "TERM.OTHER";

    private static final List<ClinicalCodeMappingAnchorDefinition> DEFINITIONS = List.of(
        definition(CanonicalResourceType.PATIENT, "gender", TERM_OTHER),
        definition(CanonicalResourceType.PATIENT, "specialPopulations", TERM_OTHER),
        definition(CanonicalResourceType.ALLERGY_INTOLERANCE, "code", TERM_DRUG),
        definition(CanonicalResourceType.ALLERGY_INTOLERANCE, "category", TERM_OTHER),
        definition(CanonicalResourceType.ALLERGY_INTOLERANCE, "criticality", TERM_OTHER),
        definition(CanonicalResourceType.ALLERGY_INTOLERANCE, "reactions", TERM_OTHER),
        definition(CanonicalResourceType.ENCOUNTER, "encounterType", TERM_OTHER),
        definition(CanonicalResourceType.ENCOUNTER, "departmentId", TERM_DEPARTMENT),
        definition(CanonicalResourceType.CONDITION, "code", TERM_DIAGNOSIS),
        definition(CanonicalResourceType.CONDITION, "stage", TERM_DIAGNOSIS),
        definition(CanonicalResourceType.CONDITION, "severity", TERM_OTHER),
        definition(CanonicalResourceType.NURSING_ASSESSMENT, "assessmentType", TERM_OTHER),
        definition(CanonicalResourceType.NURSING_ASSESSMENT, "riskLevel", TERM_OTHER),
        definition(CanonicalResourceType.NURSING_ASSESSMENT, "status", TERM_OTHER),
        definition(CanonicalResourceType.OBSERVATION, "code", TERM_LAB),
        definition(CanonicalResourceType.OBSERVATION, "unit", TERM_OTHER),
        definition(CanonicalResourceType.OBSERVATION, "criticalFlag", TERM_OTHER),
        definition(CanonicalResourceType.DIAGNOSTIC_REPORT, "reportType", TERM_EXAM),
        definition(CanonicalResourceType.MEDICATION, "code", TERM_DRUG),
        definition(CanonicalResourceType.MEDICATION, "doseUnit", TERM_OTHER),
        definition(CanonicalResourceType.MEDICATION, "route", TERM_OTHER),
        definition(CanonicalResourceType.MEDICATION, "frequency", TERM_OTHER),
        definition(CanonicalResourceType.MEDICATION, "prescriptionStatus", TERM_OTHER),
        definition(CanonicalResourceType.PROCEDURE, "code", TERM_PROCEDURE),
        definition(CanonicalResourceType.PROCEDURE, "anesthesiaType", TERM_OTHER),
        definition(CanonicalResourceType.DOCUMENT, "documentType", TERM_DOCUMENT),
        definition(CanonicalResourceType.CARE_PLAN, "pathwayId", TERM_OTHER),
        definition(CanonicalResourceType.CARE_PLAN, "currentNodeId", TERM_OTHER),
        definition(CanonicalResourceType.CARE_PLAN, "varianceCode", TERM_OTHER),
        definition(CanonicalResourceType.FOLLOW_UP, "planType", TERM_FOLLOWUP),
        definition(CanonicalResourceType.FOLLOW_UP, "questionnaireId", TERM_FOLLOWUP),
        definition(CanonicalResourceType.FOLLOW_UP, "abnormalFlag", TERM_OTHER),
        definition(CanonicalResourceType.CLAIM, "drgCode", TERM_INSURANCE)
    );

    private ClinicalCodeMappingAnchorRegistry() {
    }

    public static List<ClinicalCodeMappingAnchorDefinition> definitions() {
        return DEFINITIONS;
    }

    public static List<ClinicalCodeMappingAnchor> fromResources(ContextSnapshotResources resources) {
        if (resources == null) {
            return List.of();
        }
        List<ClinicalCodeMappingAnchor> anchors = new ArrayList<>();
        addPatient(anchors, resources.patient());
        for (CanonicalAllergyIntolerance allergy : safe(resources.allergyIntolerances())) {
            add(anchors, CanonicalResourceType.ALLERGY_INTOLERANCE, allergy.allergyIntoleranceId(), "code",
                allergy.code(), allergy.codeSystem(), allergy.substance(), TERM_DRUG,
                allergy.sourceSystem(), allergy.sourceRecordId(), allergy.mappedVersion());
            add(anchors, CanonicalResourceType.ALLERGY_INTOLERANCE, allergy.allergyIntoleranceId(), "category",
                allergy.category(), null, allergy.category(), TERM_OTHER,
                allergy.sourceSystem(), allergy.sourceRecordId(), allergy.mappedVersion());
            add(anchors, CanonicalResourceType.ALLERGY_INTOLERANCE, allergy.allergyIntoleranceId(), "criticality",
                allergy.criticality(), null, allergy.criticality(), TERM_OTHER,
                allergy.sourceSystem(), allergy.sourceRecordId(), allergy.mappedVersion());
            for (String reaction : safe(allergy.reactions())) {
                add(anchors, CanonicalResourceType.ALLERGY_INTOLERANCE, allergy.allergyIntoleranceId(), "reactions",
                    reaction, null, reaction, TERM_OTHER,
                    allergy.sourceSystem(), allergy.sourceRecordId(), allergy.mappedVersion());
            }
        }
        for (CanonicalEncounter encounter : safe(resources.encounters())) {
            add(anchors, CanonicalResourceType.ENCOUNTER, encounter.encounterId(), "encounterType",
                encounter.encounterType(), null, encounter.encounterType(), TERM_OTHER,
                encounter.sourceSystem(), encounter.sourceRecordId(), encounter.mappedVersion());
            add(anchors, CanonicalResourceType.ENCOUNTER, encounter.encounterId(), "departmentId",
                encounter.departmentId(), null, encounter.departmentId(), TERM_DEPARTMENT,
                encounter.sourceSystem(), encounter.sourceRecordId(), encounter.mappedVersion());
        }
        for (CanonicalCondition condition : safe(resources.conditions())) {
            add(anchors, CanonicalResourceType.CONDITION, condition.conditionId(), "code",
                condition.code(), condition.codeSystem(), condition.displayName(), TERM_DIAGNOSIS,
                condition.sourceSystem(), condition.sourceRecordId(), condition.mappedVersion());
            add(anchors, CanonicalResourceType.CONDITION, condition.conditionId(), "stage",
                condition.stage(), null, condition.stage(), TERM_DIAGNOSIS,
                condition.sourceSystem(), condition.sourceRecordId(), condition.mappedVersion());
            add(anchors, CanonicalResourceType.CONDITION, condition.conditionId(), "severity",
                condition.severity(), null, condition.severity(), TERM_OTHER,
                condition.sourceSystem(), condition.sourceRecordId(), condition.mappedVersion());
        }
        for (CanonicalNursingAssessment assessment : safe(resources.nursingAssessments())) {
            add(anchors, CanonicalResourceType.NURSING_ASSESSMENT, assessment.assessmentId(), "assessmentType",
                assessment.assessmentType(), null, assessment.assessmentType(), TERM_OTHER,
                assessment.sourceSystem(), assessment.sourceRecordId(), assessment.mappedVersion());
            add(anchors, CanonicalResourceType.NURSING_ASSESSMENT, assessment.assessmentId(), "riskLevel",
                assessment.riskLevel(), null, assessment.riskLevel(), TERM_OTHER,
                assessment.sourceSystem(), assessment.sourceRecordId(), assessment.mappedVersion());
            add(anchors, CanonicalResourceType.NURSING_ASSESSMENT, assessment.assessmentId(), "status",
                assessment.status(), null, assessment.status(), TERM_OTHER,
                assessment.sourceSystem(), assessment.sourceRecordId(), assessment.mappedVersion());
        }
        for (CanonicalObservation observation : safe(resources.observations())) {
            add(anchors, CanonicalResourceType.OBSERVATION, observation.observationId(), "code",
                observation.code(), null, observation.displayName(), TERM_LAB,
                observation.sourceSystem(), observation.sourceRecordId(), observation.mappedVersion());
            add(anchors, CanonicalResourceType.OBSERVATION, observation.observationId(), "unit",
                observation.unit(), null, observation.unit(), TERM_OTHER,
                observation.sourceSystem(), observation.sourceRecordId(), observation.mappedVersion());
            add(anchors, CanonicalResourceType.OBSERVATION, observation.observationId(), "criticalFlag",
                observation.criticalFlag(), null, observation.criticalFlag(), TERM_OTHER,
                observation.sourceSystem(), observation.sourceRecordId(), observation.mappedVersion());
        }
        for (CanonicalDiagnosticReport report : safe(resources.diagnosticReports())) {
            add(anchors, CanonicalResourceType.DIAGNOSTIC_REPORT, report.reportId(), "reportType",
                report.reportType(), null, report.reportType(), TERM_EXAM,
                report.sourceSystem(), report.sourceRecordId(), report.mappedVersion());
        }
        for (CanonicalMedication medication : safe(resources.medications())) {
            add(anchors, CanonicalResourceType.MEDICATION, medication.medicationId(), "code",
                medication.code(), null, medication.displayName(), TERM_DRUG,
                medication.sourceSystem(), medication.sourceRecordId(), medication.mappedVersion());
            add(anchors, CanonicalResourceType.MEDICATION, medication.medicationId(), "doseUnit",
                medication.doseUnit(), null, medication.doseUnit(), TERM_OTHER,
                medication.sourceSystem(), medication.sourceRecordId(), medication.mappedVersion());
            add(anchors, CanonicalResourceType.MEDICATION, medication.medicationId(), "route",
                medication.route(), null, medication.route(), TERM_OTHER,
                medication.sourceSystem(), medication.sourceRecordId(), medication.mappedVersion());
            add(anchors, CanonicalResourceType.MEDICATION, medication.medicationId(), "frequency",
                medication.frequency(), null, medication.frequency(), TERM_OTHER,
                medication.sourceSystem(), medication.sourceRecordId(), medication.mappedVersion());
            add(anchors, CanonicalResourceType.MEDICATION, medication.medicationId(), "prescriptionStatus",
                medication.prescriptionStatus(), null, medication.prescriptionStatus(), TERM_OTHER,
                medication.sourceSystem(), medication.sourceRecordId(), medication.mappedVersion());
        }
        for (CanonicalProcedure procedure : safe(resources.procedures())) {
            add(anchors, CanonicalResourceType.PROCEDURE, procedure.procedureId(), "code",
                procedure.code(), null, procedure.displayName(), TERM_PROCEDURE,
                procedure.sourceSystem(), procedure.sourceRecordId(), procedure.mappedVersion());
            add(anchors, CanonicalResourceType.PROCEDURE, procedure.procedureId(), "anesthesiaType",
                procedure.anesthesiaType(), null, procedure.anesthesiaType(), TERM_OTHER,
                procedure.sourceSystem(), procedure.sourceRecordId(), procedure.mappedVersion());
        }
        for (CanonicalDocument document : safe(resources.documents())) {
            add(anchors, CanonicalResourceType.DOCUMENT, document.documentId(), "documentType",
                document.documentType(), null, document.documentType(), TERM_DOCUMENT,
                document.sourceSystem(), document.sourceRecordId(), document.mappedVersion());
        }
        for (CanonicalCarePlan carePlan : safe(resources.carePlans())) {
            add(anchors, CanonicalResourceType.CARE_PLAN, carePlan.planId(), "pathwayId",
                carePlan.pathwayId(), null, carePlan.pathwayId(), TERM_OTHER,
                carePlan.sourceSystem(), carePlan.sourceRecordId(), carePlan.mappedVersion());
            add(anchors, CanonicalResourceType.CARE_PLAN, carePlan.planId(), "currentNodeId",
                carePlan.currentNodeId(), null, carePlan.currentNodeId(), TERM_OTHER,
                carePlan.sourceSystem(), carePlan.sourceRecordId(), carePlan.mappedVersion());
            add(anchors, CanonicalResourceType.CARE_PLAN, carePlan.planId(), "varianceCode",
                carePlan.varianceCode(), null, carePlan.varianceCode(), TERM_OTHER,
                carePlan.sourceSystem(), carePlan.sourceRecordId(), carePlan.mappedVersion());
        }
        for (CanonicalFollowUp followUp : safe(resources.followUps())) {
            add(anchors, CanonicalResourceType.FOLLOW_UP, followUp.followUpId(), "planType",
                followUp.planType(), null, followUp.planType(), TERM_FOLLOWUP,
                followUp.sourceSystem(), followUp.sourceRecordId(), followUp.mappedVersion());
            add(anchors, CanonicalResourceType.FOLLOW_UP, followUp.followUpId(), "questionnaireId",
                followUp.questionnaireId(), null, followUp.questionnaireId(), TERM_FOLLOWUP,
                followUp.sourceSystem(), followUp.sourceRecordId(), followUp.mappedVersion());
            add(anchors, CanonicalResourceType.FOLLOW_UP, followUp.followUpId(), "abnormalFlag",
                followUp.abnormalFlag(), null, followUp.abnormalFlag(), TERM_OTHER,
                followUp.sourceSystem(), followUp.sourceRecordId(), followUp.mappedVersion());
        }
        for (CanonicalClaim claim : safe(resources.claims())) {
            add(anchors, CanonicalResourceType.CLAIM, claim.claimId(), "drgCode",
                claim.drgCode(), null, claim.drgCode(), TERM_INSURANCE,
                claim.sourceSystem(), claim.sourceRecordId(), claim.mappedVersion());
        }
        return List.copyOf(anchors);
    }

    private static void addPatient(List<ClinicalCodeMappingAnchor> anchors, CanonicalPatient patient) {
        if (patient == null) {
            return;
        }
        add(anchors, CanonicalResourceType.PATIENT, patient.mpi(), "gender",
            patient.gender(), null, patient.gender(), TERM_OTHER,
            patient.sourceSystem(), patient.sourceRecordId(), patient.mappedVersion());
        for (String population : safe(patient.specialPopulations())) {
            add(anchors, CanonicalResourceType.PATIENT, patient.mpi(), "specialPopulations",
                population, null, population, TERM_OTHER,
                patient.sourceSystem(), patient.sourceRecordId(), patient.mappedVersion());
        }
    }

    private static ClinicalCodeMappingAnchorDefinition definition(
            CanonicalResourceType type, String fieldName, String targetDictionaryKey) {
        return new ClinicalCodeMappingAnchorDefinition(type, fieldName, targetDictionaryKey);
    }

    private static void add(List<ClinicalCodeMappingAnchor> anchors,
                            CanonicalResourceType resourceType,
                            String resourceId,
                            String fieldName,
                            String localCode,
                            String localCodeSystem,
                            String displayName,
                            String targetDictionaryKey,
                            String sourceSystem,
                            String sourceRecordId,
                            String mappedVersion) {
        if (!hasText(resourceId) || !hasText(localCode)) {
            return;
        }
        anchors.add(new ClinicalCodeMappingAnchor(resourceType, resourceId, fieldName,
            localCode, localCodeSystem, displayName, targetDictionaryKey,
            sourceSystem, sourceRecordId, mappedVersion));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
