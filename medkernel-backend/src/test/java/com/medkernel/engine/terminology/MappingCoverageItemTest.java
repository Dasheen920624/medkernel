package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 对照覆盖状态判定单测（P5）。
 */
class MappingCoverageItemTest {

    @Test
    void classifyByStandardTermPresenceAndConfirmedMappings() {
        assertThat(MappingCoverageItem.classify(false, 0))
            .isEqualTo(MappingCoverageItem.NO_STANDARD_TERM);
        assertThat(MappingCoverageItem.classify(false, 3))
            .isEqualTo(MappingCoverageItem.NO_STANDARD_TERM);
        assertThat(MappingCoverageItem.classify(true, 0))
            .isEqualTo(MappingCoverageItem.UNMAPPED);
        assertThat(MappingCoverageItem.classify(true, 2))
            .isEqualTo(MappingCoverageItem.COVERED);
    }
}
