package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingRepository;

import org.junit.jupiter.api.Test;

/**
 * 发现标准化默认实现：唯一 CONFIRMED 本地→标准映射解析为标准编码；歧义/缺失/空码均归未映射（不猜不补）。
 */
class DefaultFindingNormalizationPortTest {

    private final StandardTermRepository standardTerms = mock(StandardTermRepository.class);
    private final TermMappingRepository mappings = mock(TermMappingRepository.class);
    private final DefaultFindingNormalizationPort port =
        new DefaultFindingNormalizationPort(standardTerms, mappings);

    @Test
    void resolvesSingleConfirmedLocalMappingToStandardCode() {
        when(mappings.findConfirmedByTenantIdAndAnchor("t-1", "HIS", "LOCAL-PNEU", "TERM.DIAGNOSIS", null))
            .thenReturn(List.of(mapping(5L)));
        when(standardTerms.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(std("ICD-PNEU")));

        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "LOCAL-PNEU", "HIS"))
            .contains("ICD-PNEU");
    }

    @Test
    void ambiguousConfirmedMappingsAreUnmapped() {
        when(mappings.findConfirmedByTenantIdAndAnchor(any(), any(), any(), any(), any()))
            .thenReturn(List.of(mapping(5L), mapping(6L)));

        assertThat(port.normalize("t-1", CanonicalResourceType.OBSERVATION, "LOCAL-X", "HIS")).isEmpty();
    }

    @Test
    void noConfirmedMappingIsUnmapped() {
        when(mappings.findConfirmedByTenantIdAndAnchor(any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        assertThat(port.normalize("t-1", CanonicalResourceType.MEDICATION, "LOCAL-Y", "HIS")).isEmpty();
    }

    @Test
    void blankCodeIsUnmapped() {
        assertThat(port.normalize("t-1", CanonicalResourceType.CONDITION, "   ", "HIS")).isEmpty();
    }

    private StandardTerm std(String code) {
        return new StandardTerm(5L, "t-1", "TERM.DIAGNOSIS", code, null, code, null, null,
            null, null, null, null, null, null, null);
    }

    private TermMapping mapping(Long standardTermId) {
        return new TermMapping(1L, "t-1", 9L, standardTermId, "HIS", null, null, null,
            null, null, null, null, null, null, null, null);
    }
}
