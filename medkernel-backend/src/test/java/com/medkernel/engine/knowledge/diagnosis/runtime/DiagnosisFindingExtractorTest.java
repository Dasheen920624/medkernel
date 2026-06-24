package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.canonical.CanonicalCondition;

import org.junit.jupiter.api.Test;

/** 发现提取器：已映射进 normalizedCodes、未映射进 unmappedFindings（部分可用、不阻断），空编码跳过。 */
class DiagnosisFindingExtractorTest {

    // stub 端口：FEVER 能标准化，其余未映射（不猜不补）
    private final FindingNormalizationPort port = (tenant, release, type, code, system) ->
        "FEVER".equals(code) ? Optional.of("STD-FEVER") : Optional.empty();
    private final DiagnosisFindingExtractor extractor = new DiagnosisFindingExtractor(port);

    @Test
    void mapsKnownAndCollectsUnmapped() {
        var findings = extractor.extract(
            "t-1", "runtime-release-1", conditionResources("FEVER", "LOCALX"));
        assertThat(findings.normalizedCodes()).containsExactly("STD-FEVER");
        assertThat(findings.unmappedFindings()).containsExactly("LOCALX");
    }

    @Test
    void blankCodesAreIgnored() {
        var findings = extractor.extract(
            "t-1", "runtime-release-1", conditionResources("FEVER", "", "  "));
        assertThat(findings.normalizedCodes()).containsExactly("STD-FEVER");
        assertThat(findings.unmappedFindings()).isEmpty();
    }

    private ContextSnapshotResources conditionResources(String... codes) {
        List<CanonicalCondition> conditions = new ArrayList<>();
        for (String code : codes) {
            conditions.add(new CanonicalCondition("c-" + code, code, "ICD-10", code,
                null, null, null, null, null, null, null, null));
        }
        return new ContextSnapshotResources(null, null, null, conditions, null, null,
            null, null, null, null, null, null, null, ContextSnapshotResources.emptyExtensions());
    }
}
