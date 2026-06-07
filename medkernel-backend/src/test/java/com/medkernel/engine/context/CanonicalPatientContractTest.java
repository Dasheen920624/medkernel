package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.canonical.CanonicalPatient;

class CanonicalPatientContractTest {

    @Test
    void patientUsesStructuredAllergyResourceInsteadOfDuplicateAllergyList() {
        assertThat(Arrays.stream(CanonicalPatient.class.getRecordComponents())
            .map(component -> component.getName()))
            .doesNotContain("allergies");
    }
}
