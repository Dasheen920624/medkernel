package com.medkernel.engine.knowledge;

/**
 * 知识版本替换时的可信分级裁决摘要。
 *
 * <p>当前 PR2 只做 B0 确定性分级比较；时效和适用域精确度在来源 / 适用域字段齐备后继续扩展。
 */
record ConflictArbitration(String summary, boolean lowAuthorityOverrideHighAuthority) {

    static ConflictArbitration between(KnowledgeAssetVersion oldActive, KnowledgeAssetVersion target) {
        if (oldActive == null || target == null || oldActive.authorityLevel() == null || target.authorityLevel() == null) {
            return new ConflictArbitration("可信分级裁决：旧版或新版缺少来源分级快照，本次不自动阻断，需由审核人按来源引用人工确认。", false);
        }
        SourceAuthorityLevel oldLevel = oldActive.authorityLevel();
        SourceAuthorityLevel targetLevel = target.authorityLevel();
        if (oldLevel == targetLevel) {
            return new ConflictArbitration(null, false);
        }
        boolean lowOverHigh = targetLevel.isLowAuthority() && oldLevel.isHighAuthority();
        if (targetLevel.rank() < oldLevel.rank()) {
            return new ConflictArbitration(
                "可信分级裁决：新版来源分级 " + targetLevel.label() + " 高于旧版 " + oldLevel.label()
                    + "，默认采用新版；时效和适用域精确度未参与本次裁决。",
                false
            );
        }
        String prefix = lowOverHigh ? "可信分级裁决：低阶来源覆盖高阶来源，" : "可信分级裁决：新版来源分级低于旧版，";
        return new ConflictArbitration(
            prefix + "新版 " + targetLevel.label() + " 低于旧版 " + oldLevel.label()
                + "，必须保留显式理由和审核留痕；时效和适用域精确度未参与本次裁决。",
            lowOverHigh
        );
    }

    boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }
}
