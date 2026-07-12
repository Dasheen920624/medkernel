package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 完整医疗资源包导入边界既防滥用，也必须容纳生产级资产清单。 */
class FullPackageImportPropertiesTest {

    @Test
    void defaultEntryLimitSupportsProductionScaleFullSnapshots() {
        FullPackageImportProperties properties = new FullPackageImportProperties();

        assertThat(properties.maxEntries()).isGreaterThanOrEqualTo(10_000);
    }
}
