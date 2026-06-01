package com.medkernel.engine.clinical.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.CanonicalResourceType;

class StandardClinicalFhirMappingRegistryTest {

    private final StandardClinicalFhirMappingRegistry registry = new StandardClinicalFhirMappingRegistry();

    @Test
    void mappingsCoverSys01TwelveCanonicalTypes() {
        assertThat(registry.mappings())
            .extracting(StandardClinicalFhirMapping::canonicalType)
            .containsExactlyInAnyOrder(CanonicalResourceType.values());
    }

    @Test
    void usesExistingFhirResourceIdWhenPresent() {
        StandardClinicalFhirReference reference = registry.reference(patient("pat-1", "Patient/fhir-pat-1"));

        assertThat(reference.canonicalType()).isEqualTo(CanonicalResourceType.PATIENT);
        assertThat(reference.localId()).isEqualTo("pat-1");
        assertThat(reference.fhirVersion()).isEqualTo("R4");
        assertThat(reference.resourceType()).isEqualTo("Patient");
        assertThat(reference.resourceId()).isEqualTo("fhir-pat-1");
        assertThat(reference.mappingStatus()).isEqualTo("FHIR_RESOURCE_ID");
    }

    @Test
    void fallsBackToLocalAuthorityIdWithoutPretendingExternalFhirIdExists() {
        StandardClinicalFhirReference reference = registry.reference(condition("cond-1", null, "pat-1"));

        assertThat(reference.canonicalType()).isEqualTo(CanonicalResourceType.CONDITION);
        assertThat(reference.localId()).isEqualTo("cond-1");
        assertThat(reference.resourceType()).isEqualTo("Condition");
        assertThat(reference.resourceId()).isEqualTo("cond-1");
        assertThat(reference.mappingStatus()).isEqualTo("LOCAL_AUTHORITY_FALLBACK");
    }

    private ClinicalPatient patient(String patientId, String fhirResourceId) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new ClinicalPatient(patientId, "tenant-A", "/platform/group/hospital/dept", "HIS", "SRC-PAT",
            fhirResourceId, "cipher-name", "张*", "cipher-id", "3301********1234",
            "cipher-phone", "138****0000", LocalDate.of(1980, 1, 1), "M", now, "tester", now,
            "tester", "trace-1");
    }

    private ClinicalCondition condition(String conditionId, String fhirResourceId, String patientId) {
        Instant now = Instant.parse("2026-06-01T00:02:00Z");
        return new ClinicalCondition(conditionId, "tenant-A", "/platform/group/hospital/dept", "EMR", "SRC-COND",
            fhirResourceId, patientId, null, "A00", "ICD-10", "标准诊断", "ACTIVE", now, "tester", now,
            "tester", "trace-1");
    }
}
