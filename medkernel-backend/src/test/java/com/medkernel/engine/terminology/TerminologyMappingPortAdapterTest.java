package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalCodeMappingAnchor;
import com.medkernel.engine.versioning.PlatformAuthority;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TerminologyMappingPortAdapterTest {

    private final StandardTermRepository standardTerms = Mockito.mock(StandardTermRepository.class);
    private final EffectiveTermMappingResolver effectiveMappings =
        Mockito.mock(EffectiveTermMappingResolver.class);
    private final TerminologyMappingPortAdapter adapter =
        new TerminologyMappingPortAdapter(standardTerms, effectiveMappings);

    @Test
    void validatesDirectStandardCodingAgainstActiveDictionaryTerm() {
        ClinicalCodeMappingAnchor anchor = anchor("718-7", "http://loinc.org", "LOINC");
        List<String> standardSources = List.of(PlatformAuthority.PLATFORM_TENANT_ID, "tenant-A");
        when(standardTerms.findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
            standardSources, "tenant-A", "LOINC", "718-7", StandardTermStatus.ACTIVE))
            .thenReturn(Optional.of(standardTerm("718-7")));

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "VALID");
    }

    @Test
    void validatesLocalCodingThroughActivePackageSnapshot() {
        ClinicalCodeMappingAnchor anchor = anchor("HB", "LIS", "LOINC");
        when(effectiveMappings.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .thenReturn(List.of(new EffectiveTermMapping(1L, 2L, "718-7")));

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "VALID");
    }

    @Test
    void ignoresConfirmedMappingThatHasNotEnteredAnActivePackage() {
        ClinicalCodeMappingAnchor anchor = anchor("HB", "LIS", "LOINC");
        when(effectiveMappings.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .thenReturn(List.of());

        Map<String, String> result = adapter.evaluate("tenant-A", List.of(anchor));

        assertThat(result).containsEntry(anchor.key(), "UNKNOWN");
        verify(standardTerms, never()).findFirstByTenantIdsAndId(
            Mockito.anyList(), Mockito.anyString(), Mockito.anyLong());
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

}
