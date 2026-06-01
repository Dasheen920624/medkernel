package com.medkernel.engine.clinical.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * SYS-01 标准临床模型静态契约。
 */
class StandardClinicalModelContractTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
        "mk_clinical_patient",
        "mk_clinical_encounter",
        "mk_clinical_condition",
        "mk_clinical_observation",
        "mk_clinical_medication",
        "mk_clinical_procedure",
        "mk_clinical_diagnostic_report",
        "mk_clinical_document",
        "mk_clinical_nursing_assessment",
        "mk_clinical_care_plan",
        "mk_clinical_follow_up",
        "mk_clinical_claim"
    );

    @Test
    void clinicalModelHasExactlySys01TwelveObjectTables() {
        assertThat(StandardClinicalTables.names()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void patientModelDoesNotExposePlainSensitiveFields() {
        assertThat(ClinicalPatient.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .doesNotContain("name", "identityNo", "phone")
            .contains("nameCipher", "nameMask", "identityNoCipher", "identityNoMask", "phoneCipher", "phoneMask");
    }

    @Test
    void oldSymptomCanonicalResourceIsNotPartOfSys01() {
        assertThat(CanonicalResourceType.values())
            .extracting(Enum::name)
            .doesNotContain("SYMPTOM")
            .contains("NURSING_ASSESSMENT");
    }
}
