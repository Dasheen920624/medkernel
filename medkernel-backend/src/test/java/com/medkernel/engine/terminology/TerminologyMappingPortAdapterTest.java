package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalCodeMappingAnchor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TerminologyMappingPortAdapterTest {

    private final StandardTermRepository standardTerms = Mockito.mock(StandardTermRepository.class);
    private final TermMappingRepository mappings = Mockito.mock(TermMappingRepository.class);
    private final TerminologyMappingPortAdapter adapter =
        new TerminologyMappingPortAdapter(standardTerms, mappings);

    @Test
    void validatesDirectStandardCodingAgainstActiveDictionaryTerm() {
        ClinicalCodeMappingAnchor anchor = anchor("718-7", "http://loinc.org", "LOINC");
        when(standardTerms.findByTenantIdAndStandardSystemAndTermCodeAndStatus(
            "tenant-A", "LOINC", "718-7", StandardTermStatus.ACTIVE))
            .thenReturn(Optional.of(standardTerm("718-7")));

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "VALID");
    }

    @Test
    void validatesLocalCodingThroughConfirmedMappingWithoutLcsFallback() {
        ClinicalCodeMappingAnchor anchor = anchor("HB", "LIS", "LOINC");
        when(mappings.findConfirmedByTenantIdAndAnchor("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .thenReturn(List.of(mapping(1L)));

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "VALID");
    }

    @Test
    void marksUnknownWhenNoConfirmedMappingExists() {
        ClinicalCodeMappingAnchor anchor = anchor("HB", "LIS", "LOINC");
        when(mappings.findConfirmedByTenantIdAndAnchor("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .thenReturn(List.of());

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "UNKNOWN");
    }

    private static ClinicalCodeMappingAnchor anchor(String code, String sourceSystem, String targetDictionary) {
        return new ClinicalCodeMappingAnchor(
            CanonicalResourceType.OBSERVATION,
            "obs-1",
            "code",
            code,
            sourceSystem,
            "血红蛋白",
            targetDictionary,
            "FHIR",
            "Observation/obs-1",
            "FHIR_R4:Observation");
    }

    private static StandardTerm standardTerm(String code) {
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        return new StandardTerm(
            2L,
            "tenant-A",
            "LOINC",
            code,
            TermCategory.LAB,
            "血红蛋白",
            "xuehongdanbai",
            "LOINC-2026",
            StandardTermStatus.ACTIVE,
            null,
            "来源：LOINC 2026",
            now,
            "tester",
            now,
            "tester");
    }

    private static TermMapping mapping(Long id) {
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        return new TermMapping(
            id,
            "tenant-A",
            1L,
            2L,
            "LIS",
            TermCategory.LAB,
            1.0D,
            TermRiskLevel.LOW,
            TermMappingStatus.CONFIRMED,
            "人工确认 LIS:HB -> LOINC:718-7",
            "tester",
            now,
            now,
            "tester",
            now,
            "tester");
    }
}
