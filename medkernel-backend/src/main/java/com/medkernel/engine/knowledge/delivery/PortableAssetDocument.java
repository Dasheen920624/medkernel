package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * `.mkp` 中单个可恢复版本化资产的规范文档。
 *
 * <p>只携带稳定身份、完整正文、许可、精确依赖和确定性验证事实，不携带数据库主键、宿主信息、
 * evidenceId、导出时间或运行期患者数据。
 *
 * @param schemaVersion 资产文档模式版本
 * @param assetType 统一版本化资产类型
 * @param assetIdentity 跨实例稳定资产身份
 * @param versionId 跨实例不可变版本标识
 * @param versionNo 业务版本号
 * @param organizationScope 资产适用组织范围
 * @param applicableScope 资产业务适用范围
 * @param safetyPolicy 医疗安全继承策略
 * @param overridePolicy 下游覆盖策略护栏
 * @param contentSha256 可恢复规范正文的 SHA-256 摘要
 * @param contentDigest 规范正文的 SM3 摘要
 * @param content 可完整恢复的规范正文
 * @param sources 全部来源与精确引用锚点
 * @param licenses 全部再分发许可事实及其文件摘要
 * @param dependencies 精确版本依赖
 * @param validation 发布前类型校验事实
 * @param testVectors 可重放合成测试向量及预期结果
 */
public record PortableAssetDocument(
    String schemaVersion,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String versionNo,
    String organizationScope,
    String applicableScope,
    AssetVersionSafetyPolicy safetyPolicy,
    AssetVersionOverridePolicy overridePolicy,
    String contentSha256,
    String contentDigest,
    JsonNode content,
    List<Source> sources,
    List<License> licenses,
    List<Dependency> dependencies,
    Validation validation,
    List<TestVector> testVectors
) {

    /**
     * 导出适配器的规范输入；contentDigest 由适配器按规范正文计算，调用方不得自报。
     */
    public record ExportInput(
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId,
        String versionNo,
        String organizationScope,
        String applicableScope,
        AssetVersionSafetyPolicy safetyPolicy,
        AssetVersionOverridePolicy overridePolicy,
        JsonNode content,
        List<Source> sources,
        List<License> licenses,
        List<Dependency> dependencies,
        Validation validation,
        List<TestVector> testVectors
    ) {
    }

    /**
     * 可追溯来源。
     *
     * @param sourceType 来源类型
     * @param title 来源标题
     * @param sourceVersion 来源版本
     * @param citationAnchor 精确引用锚点
     * @param originalDigest 来源原文或获准派生物摘要
     * @param licenseId 适用于该来源内容的许可稳定标识
     */
    public record Source(
        String sourceType,
        String title,
        String sourceVersion,
        String citationAnchor,
        String originalDigest,
        String licenseId
    ) {
    }

    /**
     * 目标医院交付许可。
     *
     * @param licenseId 稳定许可标识
     * @param redistributionAllowed 是否允许再分发
     * @param redistributionScope 允许交付的目标与内容范围
     * @param licenseDigest 许可文件或不可变许可记录摘要
     */
    public record License(
        String licenseId,
        boolean redistributionAllowed,
        String redistributionScope,
        String licenseDigest
    ) {
    }

    /**
     * 精确资产版本依赖。
     *
     * @param assetType 被依赖资产类型
     * @param assetIdentity 被依赖稳定身份
     * @param versionId 被依赖不可变版本标识
     * @param versionNo 被依赖业务版本号
     * @param contentDigest 被依赖正文摘要
     * @param dependencyKind 依赖语义
     */
    public record Dependency(
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId,
        String versionNo,
        String contentDigest,
        AssetDependencyKind dependencyKind
    ) {
    }

    /**
     * 不含本地证据主键和时间的确定性校验事实。
     *
     * @param profile 校验规约稳定标识
     * @param passed 是否通过
     * @param versionId 校验事实绑定的不可变资产版本
     * @param resultDigest 完整校验结果摘要
     */
    public record Validation(
        String profile,
        boolean passed,
        String versionId,
        String resultDigest
    ) {
    }

    /**
     * 不对应真实个人的可重放验证向量。
     *
     * @param vectorId 向量稳定标识
     * @param input 合成输入
     * @param expected 预期结果
     * @param syntheticProvenance 可回读的确定性合成生成证明
     */
    public record TestVector(
        String vectorId,
        JsonNode input,
        JsonNode expected,
        SyntheticProvenance syntheticProvenance
    ) {
    }

    /**
     * 合成测试向量生成证明；实际生成清单由摘要绑定进包，不以布尔声明冒充证明。
     *
     * @param generatorId 确定性合成生成器稳定标识
     * @param generatorVersion 生成器版本
     * @param scenarioId 非真实个人的场景模板稳定标识
     * @param manifestDigest 生成清单摘要
     */
    public record SyntheticProvenance(
        String generatorId,
        String generatorVersion,
        String scenarioId,
        String manifestDigest
    ) {
    }
}
