package com.medkernel.engine.versioning;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可发布配置资产唯一生命周期契约。
 *
 * <p>患者实例、生成作业、导入导出和执行记录拥有各自过程状态，不受本契约限制。
 */
class AssetLifecycleContractTest {

    @Test
    void contentVersionsUseOnlyDraftPublishedAndWithdrawn() {
        assertThat(names(AssetVersionStatus.values()))
            .containsExactly("DRAFT", "PUBLISHED", "WITHDRAWN");
    }

    @Test
    void runtimeAssetCatalogDoesNotTreatReleaseContainerOrRuntimeRecommendationAsAssets() {
        assertThat(Arrays.stream(VersionedAssetType.values())
            .filter(VersionedAssetType::isRuntimeConfiguration)
            .map(Enum::name)
            .toArray(String[]::new))
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
            .doesNotContain("PACKAGE", "RECOMMENDATION");
    }

    @Test
    void versionedAssetTypeCatalogContainsOnlyTheThirteenRuntimeAssets() {
        assertThat(names(VersionedAssetType.values()))
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
                "ACTION_CARD");
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values)
            .map(Enum::name)
            .toArray(String[]::new);
    }
}
