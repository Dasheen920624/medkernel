package com.medkernel.engine.factory;

import java.util.List;

import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * AI 工厂全类资产的统一产出信封（AIK-STD-01，FR-1/2）。
 *
 * <p>生产器（① API 大模型 / ② Agent 工具协助 / ③ 本地大模型 / ④ 人工）统一产出此信封，
 * 经 {@link KnowledgeAssetSchemaValidator} 入既有版本/审核/替换链前校验。信封类型无关，类型特定内容置 {@code payload}；
 * 元数据复用既有枚举（资产类型 {@link VersionedAssetType} / 可信分级 {@link SourceAuthorityLevel} /
 * GRADE / 风险 / 生命周期状态 {@link AssetVersionStatus}），不新建存储表（持久化由 AIK-STD-13 编排落既有链）。
 *
 * @param assetType 资产类型（全类统一枚举）
 * @param assetIdentity 资产身份键
 * @param subject 资产主题（人读标签）
 * @param versionLabel 版本标签
 * @param sources 来源/引用绑定（≥1，无源拒收）
 * @param trustLevel 可信分级（OPT-07 五级）
 * @param gradeQuality GRADE 证据质量（可空）
 * @param gradeStrength GRADE 推荐强度（可空）
 * @param riskLevel 风险级别
 * @param orgScope 组织作用域
 * @param contentHash 内容 SHA-256 指纹（须与 payload 真实一致）
 * @param payload 类型化内容
 * @param lifecycleStatus 生命周期状态（生产器只产候选态）
 */
public record KnowledgeAssetEnvelope(
    VersionedAssetType assetType,
    String assetIdentity,
    String subject,
    String versionLabel,
    List<AssetSourceRef> sources,
    SourceAuthorityLevel trustLevel,
    GradeEvidenceQuality gradeQuality,
    GradeRecommendationStrength gradeStrength,
    KnowledgeRiskLevel riskLevel,
    String orgScope,
    String contentHash,
    String payload,
    AssetVersionStatus lifecycleStatus
) {

    public KnowledgeAssetEnvelope {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
