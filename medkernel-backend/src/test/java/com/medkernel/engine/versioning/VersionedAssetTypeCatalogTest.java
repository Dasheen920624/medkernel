package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VersionedAssetTypeCatalogTest {

    @Test
    void onlyContainsIndependentlyMaintainedAssets() {
        assertThat(Arrays.stream(VersionedAssetType.values()).map(Enum::name))
            .containsExactly(
                "KNOWLEDGE",
                "TERMINOLOGY",
                "RULE",
                "PATHWAY",
                "EVALUATION",
                "FOLLOWUP",
                "FIELD_CATALOG",
                "SAFETY",
                "CDSS_RISK",
                "VALUE_SET",
                "FORMULA",
                "ORDER_SET",
                "ACTION_CARD")
            .doesNotContain("PACKAGE", "RECOMMENDATION")
            .doesNotContain("CONDITION_FRAGMENT");
    }
}
