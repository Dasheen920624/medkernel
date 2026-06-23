package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalPatient;

class ContextValidatorTest {

    private final ContextValidator validator = new ContextValidator();

    @Test
    void shouldDetectMissingPatientBirthDateAsWarn() {
        var patient = new CanonicalPatient("MPI-1", "张三", null, "M",
            List.of(), "HIS", "rec-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.PARTIAL);
        var resources = emptyResources(patient);

        List<MissingFieldEntry> missing = validator.findMissingFields(resources);

        assertThat(missing).anyMatch(m ->
            "PATIENT".equals(m.resourceType()) && "birthDate".equals(m.field()) && "WARN".equals(m.level()));
    }

    @Test
    void shouldComputeOverallQualityAsPartialWhenAnyResourcePartial() {
        var patient = new CanonicalPatient("MPI-1", "张三", LocalDate.of(1980, 1, 1), "M",
            List.of(), "HIS", "rec-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.PARTIAL);
        var resources = emptyResources(patient);

        assertThat(validator.computeQuality(resources)).isEqualTo(QualityStatus.PARTIAL);
    }

    @Test
    void shouldComputeOverallQualityAsInvalidWhenAnyResourceInvalid() {
        var patient = new CanonicalPatient("MPI-1", "张三", LocalDate.of(1980, 1, 1), "M",
            List.of(), "HIS", "rec-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.INVALID);
        var resources = emptyResources(patient);

        assertThat(validator.computeQuality(resources)).isEqualTo(QualityStatus.INVALID);
    }

    @Test
    void shouldReturnCriticalWhenPatientIsNull() {
        var resources = emptyResources(null);

        List<MissingFieldEntry> missing = validator.findMissingFields(resources);
        assertThat(missing).extracting(MissingFieldEntry::level).contains("CRITICAL");
        assertThat(validator.computeQuality(resources)).isEqualTo(QualityStatus.INVALID);
    }

    @Test
    void shouldFlagEncounterAdmissionTimeMissingAsError() {
        var patient = new CanonicalPatient("MPI-1", "张三", LocalDate.of(1980, 1, 1), "M",
            List.of(), "HIS", "rec-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.VALID);
        var enc = new CanonicalEncounter("ENC-1", "INPATIENT", null, null,
            "DEPT-A", "DOC-A", null, "HIS", "rec-2", "v1",
            Instant.now(), Instant.now(), QualityStatus.PARTIAL);
        var resources = new ContextSnapshotResources(patient,
            List.of(), List.of(enc), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());

        List<MissingFieldEntry> missing = validator.findMissingFields(resources);
        assertThat(missing).anyMatch(m ->
            "ENCOUNTER".equals(m.resourceType())
                && "admissionTime".equals(m.field())
                && "ERROR".equals(m.level()));
    }

    private ContextSnapshotResources emptyResources(CanonicalPatient patient) {
        return new ContextSnapshotResources(patient,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());
    }
}
