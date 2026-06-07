package com.medkernel.engine.clinical.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * SYS-01 标准临床对象到 FHIR R4 资源引用的显式映射注册表。
 */
@Component
public class StandardClinicalFhirMappingRegistry {

    public static final String FHIR_VERSION_R4 = "R4";
    public static final String STATUS_FHIR_RESOURCE_ID = "FHIR_RESOURCE_ID";
    public static final String STATUS_LOCAL_AUTHORITY_FALLBACK = "LOCAL_AUTHORITY_FALLBACK";

    private static final List<StandardClinicalFhirMapping> MAPPINGS = List.of(
        mapping(CanonicalResourceType.PATIENT, StandardClinicalFhirResourceType.PATIENT),
        mapping(CanonicalResourceType.ALLERGY_INTOLERANCE, StandardClinicalFhirResourceType.ALLERGY_INTOLERANCE),
        mapping(CanonicalResourceType.ENCOUNTER, StandardClinicalFhirResourceType.ENCOUNTER),
        mapping(CanonicalResourceType.CONDITION, StandardClinicalFhirResourceType.CONDITION),
        mapping(CanonicalResourceType.NURSING_ASSESSMENT, StandardClinicalFhirResourceType.OBSERVATION),
        mapping(CanonicalResourceType.OBSERVATION, StandardClinicalFhirResourceType.OBSERVATION),
        mapping(CanonicalResourceType.DIAGNOSTIC_REPORT, StandardClinicalFhirResourceType.DIAGNOSTIC_REPORT),
        mapping(CanonicalResourceType.MEDICATION, StandardClinicalFhirResourceType.MEDICATION_REQUEST),
        mapping(CanonicalResourceType.PROCEDURE, StandardClinicalFhirResourceType.PROCEDURE),
        mapping(CanonicalResourceType.DOCUMENT, StandardClinicalFhirResourceType.DOCUMENT_REFERENCE),
        mapping(CanonicalResourceType.CARE_PLAN, StandardClinicalFhirResourceType.CARE_PLAN),
        mapping(CanonicalResourceType.FOLLOW_UP, StandardClinicalFhirResourceType.TASK),
        mapping(CanonicalResourceType.CLAIM, StandardClinicalFhirResourceType.CLAIM)
    );

    private final Map<CanonicalResourceType, StandardClinicalFhirResourceType> resourceTypes =
        new EnumMap<>(CanonicalResourceType.class);

    public StandardClinicalFhirMappingRegistry() {
        for (StandardClinicalFhirMapping mapping : MAPPINGS) {
            resourceTypes.put(mapping.canonicalType(), mapping.fhirResourceType());
        }
    }

    public List<StandardClinicalFhirMapping> mappings() {
        return MAPPINGS;
    }

    public StandardClinicalFhirReference reference(ClinicalPatient patient) {
        return reference(CanonicalResourceType.PATIENT, patient.patientId(), patient.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalEncounter encounter) {
        return reference(CanonicalResourceType.ENCOUNTER, encounter.encounterId(), encounter.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalCondition condition) {
        return reference(CanonicalResourceType.CONDITION, condition.conditionId(), condition.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalNursingAssessment assessment) {
        return reference(CanonicalResourceType.NURSING_ASSESSMENT,
            assessment.assessmentId(), assessment.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalObservation observation) {
        return reference(CanonicalResourceType.OBSERVATION, observation.observationId(), observation.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalDiagnosticReport report) {
        return reference(CanonicalResourceType.DIAGNOSTIC_REPORT, report.reportId(), report.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalMedication medication) {
        return reference(CanonicalResourceType.MEDICATION, medication.medicationId(), medication.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalProcedure procedure) {
        return reference(CanonicalResourceType.PROCEDURE, procedure.procedureId(), procedure.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalDocument document) {
        return reference(CanonicalResourceType.DOCUMENT, document.documentId(), document.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalCarePlan carePlan) {
        return reference(CanonicalResourceType.CARE_PLAN, carePlan.carePlanId(), carePlan.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalFollowUp followUp) {
        return reference(CanonicalResourceType.FOLLOW_UP, followUp.followUpId(), followUp.fhirResourceId());
    }

    public StandardClinicalFhirReference reference(ClinicalClaim claim) {
        return reference(CanonicalResourceType.CLAIM, claim.claimId(), claim.fhirResourceId());
    }

    private StandardClinicalFhirReference reference(
            CanonicalResourceType canonicalType, String localId, String fhirResourceId) {
        String resourceType = resourceType(canonicalType).resourceType();
        if (fhirResourceId != null && !fhirResourceId.isBlank()) {
            String resourceId = parseResourceId(resourceType, fhirResourceId);
            return new StandardClinicalFhirReference(
                canonicalType, localId, FHIR_VERSION_R4, resourceType, resourceId, STATUS_FHIR_RESOURCE_ID);
        }
        return new StandardClinicalFhirReference(
            canonicalType, localId, FHIR_VERSION_R4, resourceType, localId, STATUS_LOCAL_AUTHORITY_FALLBACK);
    }

    private StandardClinicalFhirResourceType resourceType(CanonicalResourceType canonicalType) {
        StandardClinicalFhirResourceType type = resourceTypes.get(canonicalType);
        if (type == null) {
            throw new IllegalArgumentException("未注册 FHIR 映射的标准资源类型: " + canonicalType);
        }
        return type;
    }

    private static String parseResourceId(String expectedResourceType, String fhirResourceId) {
        String[] parts = fhirResourceId.split("/", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            if (!expectedResourceType.equals(parts[0])) {
                throw new IllegalArgumentException(
                    "FHIR 资源类型不匹配: expected=" + expectedResourceType + " actual=" + parts[0]);
            }
            return parts[1];
        }
        return fhirResourceId;
    }

    private static StandardClinicalFhirMapping mapping(
            CanonicalResourceType type, StandardClinicalFhirResourceType fhirResourceType) {
        return new StandardClinicalFhirMapping(type, fhirResourceType);
    }
}
