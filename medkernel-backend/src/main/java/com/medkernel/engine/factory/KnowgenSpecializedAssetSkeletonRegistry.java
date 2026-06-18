package com.medkernel.engine.factory;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * T7.2 KNOWGEN 专用资产类型代码骨架目录。
 *
 * <p>覆盖评分计算器、规则测试病例、合理用检、核心制度核查、特殊人群剂量与 PGx。目录本身不生产内容，
 * 只为后续真实来源导入提供生成/校验/计算的可运行骨架。
 */
@Service
public class KnowgenSpecializedAssetSkeletonRegistry {

    private static final List<String> BASE_CAPABILITIES = List.of("GENERATE_DRAFT", "VALIDATE_STRUCTURE");

    private static final List<KnowgenSpecializedAssetSkeleton> SKELETONS = List.of(
        skeleton("KNOWGEN-16", "评分量表与计算器", List.of(VersionedAssetType.FORMULA),
            List.of("inputs", "algorithm", "thresholds", "test_vectors", "source"),
            List.of("GENERATE_DRAFT", "VALIDATE_STRUCTURE", "CALCULATE_FORMULA")),
        skeleton("KNOWGEN-04", "临床规则与测试病例", List.of(VersionedAssetType.RULE),
            List.of("trigger", "logic", "action", "risk", "test_cases", "source"),
            BASE_CAPABILITIES),
        skeleton("KNOWGEN-18", "检查检验适当性规则", List.of(VersionedAssetType.RULE),
            List.of("indication", "contraindication", "logic", "action", "test_cases", "source"),
            BASE_CAPABILITIES),
        skeleton("KNOWGEN-20", "18 项医疗核心制度核查", List.of(VersionedAssetType.RULE),
            List.of("policy_basis", "scenario", "logic", "risk", "test_cases", "source"),
            BASE_CAPABILITIES),
        skeleton("KNOWGEN-19", "特殊人群剂量与 PGx", List.of(VersionedAssetType.RULE, VersionedAssetType.FORMULA),
            List.of("special_population", "dose_adjustment", "pgx_guidance", "review_policy", "source"),
            List.of("GENERATE_DRAFT", "VALIDATE_STRUCTURE", "VALIDATE_DOSAGE_PGX_STRUCTURE",
                "REQUIRE_HIGH_RISK_REVIEW"))
    );

    /** 返回 T7.2 覆盖的 5 类专用代码骨架。 */
    public List<KnowgenSpecializedAssetSkeleton> listT72Skeletons() {
        return SKELETONS;
    }

    /** 按 KNOWGEN 卡号查询专用骨架。 */
    public KnowgenSpecializedAssetSkeleton require(String cardCode) {
        String normalized = cardCode == null ? "" : cardCode.trim().toUpperCase(Locale.ROOT);
        return SKELETONS.stream()
            .filter(skeleton -> skeleton.cardCode().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "KNOWGEN 专用骨架不存在: " + normalized));
    }

    private static KnowgenSpecializedAssetSkeleton skeleton(String cardCode, String displayName,
            List<VersionedAssetType> assetTypes, List<String> requiredPayloadFields, List<String> capabilities) {
        return new KnowgenSpecializedAssetSkeleton(cardCode, displayName, assetTypes, requiredPayloadFields,
            capabilities, true, false, false);
    }
}
