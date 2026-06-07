package com.medkernel.engine.clinical.model;

/**
 * SYS-01 标准临床对象对应的 FHIR R4 资源类型。
 */
public enum StandardClinicalFhirResourceType {
    PATIENT("Patient"),
    ALLERGY_INTOLERANCE("AllergyIntolerance"),
    ENCOUNTER("Encounter"),
    CONDITION("Condition"),
    OBSERVATION("Observation"),
    MEDICATION_REQUEST("MedicationRequest"),
    PROCEDURE("Procedure"),
    DIAGNOSTIC_REPORT("DiagnosticReport"),
    DOCUMENT_REFERENCE("DocumentReference"),
    CARE_PLAN("CarePlan"),
    TASK("Task"),
    CLAIM("Claim");

    private final String resourceType;

    StandardClinicalFhirResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String resourceType() {
        return resourceType;
    }
}
