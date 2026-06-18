package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * T7.2 KNOWGEN 资产类型专用代码骨架测试。
 */
class KnowgenSpecializedAssetSkeletonRegistryTest {

    private final KnowgenSpecializedAssetSkeletonRegistry registry =
        new KnowgenSpecializedAssetSkeletonRegistry();

    @Test
    void coversT72KnowgenCardsWithB0CodeCapabilitiesAndNoClinicalContentSeeds() {
        List<KnowgenSpecializedAssetSkeleton> skeletons = registry.listT72Skeletons();

        assertThat(skeletons)
            .extracting(KnowgenSpecializedAssetSkeleton::cardCode)
            .containsExactly("KNOWGEN-16", "KNOWGEN-04", "KNOWGEN-18", "KNOWGEN-20", "KNOWGEN-19");
        assertThat(skeletons).allSatisfy(skeleton -> {
            assertThat(skeleton.b0Executable()).isTrue();
            assertThat(skeleton.modelRequired()).isFalse();
            assertThat(skeleton.clinicalContentSeeded()).isFalse();
            assertThat(skeleton.requiredPayloadFields()).isNotEmpty();
            assertThat(skeleton.codeCapabilities()).contains("GENERATE_DRAFT", "VALIDATE_STRUCTURE");
        });
    }

    @Test
    void calculatorAndPgxSkeletonsExposeSpecializedCodeCapabilities() {
        KnowgenSpecializedAssetSkeleton calculator = registry.require("KNOWGEN-16");
        KnowgenSpecializedAssetSkeleton pgx = registry.require("KNOWGEN-19");

        assertThat(calculator.assetTypes()).containsExactly(VersionedAssetType.FORMULA);
        assertThat(calculator.codeCapabilities()).contains("CALCULATE_FORMULA");
        assertThat(calculator.requiredPayloadFields())
            .contains("inputs", "algorithm", "thresholds", "test_vectors", "source");

        assertThat(pgx.assetTypes()).containsExactly(VersionedAssetType.RULE, VersionedAssetType.FORMULA);
        assertThat(pgx.codeCapabilities()).contains("VALIDATE_DOSAGE_PGX_STRUCTURE", "REQUIRE_HIGH_RISK_REVIEW");
        assertThat(pgx.requiredPayloadFields())
            .contains("special_population", "dose_adjustment", "pgx_guidance", "review_policy", "source");
    }
}
