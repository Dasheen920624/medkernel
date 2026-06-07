package com.medkernel.engine.context.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.QualityStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CanonicalDtoValidationTest {

    private final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void canonicalPatientRequiresMpiAndName() {
        var invalid = new CanonicalPatient(null, null, null, null, List.of(),
            "HIS", "REC-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        Set<ConstraintViolation<CanonicalPatient>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
            .contains("mpi", "name");
    }

    @Test
    void canonicalConditionRequiresCodeDisplayAndCodeSystem() {
        var invalid = new CanonicalCondition(null, null, null, null, null, null,
            "EMR", "REC-2", "v1", null, Instant.now(), QualityStatus.PARTIAL);
        Set<ConstraintViolation<CanonicalCondition>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
            .contains("conditionId", "code", "codeSystem", "displayName");
    }

    @Test
    void canonicalEncounterRequiresAdmissionTime() {
        var invalid = new CanonicalEncounter(null, null, null, null, null, null, null,
            "HIS", "REC-3", "v1", Instant.now(), Instant.now(), QualityStatus.PARTIAL);
        Set<ConstraintViolation<CanonicalEncounter>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
            .contains("encounterId", "encounterType", "admissionTime");
    }

    @Test
    void canonicalEncounterRejectsNonCanonicalClinicalSetting() {
        assertThatThrownBy(() -> new CanonicalEncounter(
            "enc-1", "IP", Instant.now(), null, null, null, null,
            "HIS", "REC-3", "v1", Instant.now(), Instant.now(), QualityStatus.VALID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INPATIENT/OUTPATIENT/ED/FOLLOWUP");
    }

    @Test
    void canonicalMedicationRequiresCodeAndDisplay() {
        var invalid = new CanonicalMedication(null, null, null, null, null, null, null, null, null,
            "HIS", "REC-4", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        Set<ConstraintViolation<CanonicalMedication>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
            .contains("medicationId", "code", "displayName");
    }

    @Test
    void canonicalNursingAssessmentRequiresIdentityAndType() {
        var invalid = new CanonicalNursingAssessment(null, null, null, null,
            "NIS", "REC-5", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        Set<ConstraintViolation<CanonicalNursingAssessment>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
            .contains("assessmentId", "assessmentType");
    }
}
