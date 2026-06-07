package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.terminology.EffectiveTermMapping;
import com.medkernel.engine.terminology.EffectiveTermMappingResolver;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.versioning.PlatformAuthority;

import org.junit.jupiter.api.Test;

/**
 * 发现标准化默认实现：唯一 CONFIRMED 本地→标准映射解析为标准编码；歧义/缺失/空码均归未映射（不猜不补）。
 */
class DefaultFindingNormalizationPortTest {

    private final StandardTermRepository standardTerms = mock(StandardTermRepository.class);
    private final EffectiveTermMappingResolver effectiveMappings = mock(EffectiveTermMappingResolver.class);
    private final DefaultFindingNormalizationPort port =
        new DefaultFindingNormalizationPort(standardTerms, effectiveMappings);

    @Test
    void resolvesSingleActivePackageSnapshotToStandardCode() {
        when(effectiveMappings.resolve(
                "t-1", "HIS", "LOCAL-PNEU", "TERM.DIAGNOSIS", null))
            .thenReturn(List.of(new EffectiveTermMapping(1L, 5L, "ICD-PNEU")));

        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "LOCAL-PNEU", "HIS"))
            .contains("ICD-PNEU");
    }

    @Test
    void ambiguousActivePackageMappingsAreUnmapped() {
        when(effectiveMappings.resolve(any(), any(), any(), any(), any()))
            .thenReturn(List.of(
                new EffectiveTermMapping(1L, 5L, "ICD-A"),
                new EffectiveTermMapping(2L, 6L, "ICD-B")));

        assertThat(port.normalize("t-1", CanonicalResourceType.OBSERVATION, "LOCAL-X", "HIS")).isEmpty();
    }

    @Test
    void confirmedButUnreleasedMappingIsUnmapped() {
        when(effectiveMappings.resolve(any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        assertThat(port.normalize("t-1", CanonicalResourceType.MEDICATION, "LOCAL-Y", "HIS")).isEmpty();
    }

    @Test
    void blankCodeIsUnmapped() {
        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "   ", "HIS")).isEmpty();
    }

    @Test
    void passesThroughLocalCodeThatIsItselfActiveStandardTerm() {
        // 院内直接用标准字典编码：无本地→标准映射，但 localCode 本身是该字典 ACTIVE 标准码 → 透传
        List<String> standardSources = List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        when(effectiveMappings.resolve(any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
                standardSources, "t-1", "TERM.DIAGNOSIS", "ICD-PNEU"))
            .thenReturn(Optional.of(std("ICD-PNEU")));

        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "ICD-PNEU", "ICD-10"))
            .contains("ICD-PNEU");
    }

    @Test
    void activePackageMappingTakesPrecedenceOverPassthrough() {
        when(effectiveMappings.resolve(
                "t-1", "HIS", "LOCAL-PNEU", "TERM.DIAGNOSIS", null))
            .thenReturn(List.of(new EffectiveTermMapping(1L, 5L, "ICD-MAPPED")));

        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "LOCAL-PNEU", "HIS"))
            .contains("ICD-MAPPED");
        verify(standardTerms, never())
            .findFirstActiveByTenantIdsAndStandardSystemAndTermCode(any(), any(), any(), any());
    }

    private StandardTerm std(String code) {
        return new StandardTerm(5L, "t-1", "TERM.DIAGNOSIS", code, null, code, null, null,
            null, null, null, null, null, null, null);
    }

}
