package com.medkernel.engine.versioning;

/**
 * 安全单调性谓词（SPI）：判定某资产类型下，下级覆盖版本相对被锁定的平台/上级基线是否“至少同样严格”
 * （只收紧、不放宽安全约束）。各业务域（给药禁忌、剂量上限、过敏核查、红线触发等）按自身语义实现；
 * 解析层 {@link InheritanceResolver} 对 {@code override_policy=LOCKED} 基线的内容变更覆盖统一调用：
 * 仅当存在适配该资产类型的谓词且其返回 {@code true} 时放行覆盖，否则保守拒绝并回退继承锁定版本。
 *
 * <p>见设计附录 S2（安全单调性）。本接口为扩展点，具体领域实现随各域接入逐步提供。
 */
public interface SafetyMonotonicityCheck {

    /** 是否适配该资产类型。 */
    boolean supports(VersionedAssetType assetType);

    /**
     * 覆盖版本相对被锁定基线是否未放宽安全约束。
     *
     * @param lockedBaseline    被继承的锁定平台/上级版本
     * @param candidateOverride 下级 REPLACE 覆盖版本
     * @return {@code true} 表示“至少同样严格”（可放行），{@code false} 表示放宽（须拒绝）
     */
    boolean isAtLeastAsStrict(AssetVersion lockedBaseline, AssetVersion candidateOverride);
}
