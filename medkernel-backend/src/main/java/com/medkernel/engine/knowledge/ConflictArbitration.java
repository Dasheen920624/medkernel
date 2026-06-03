package com.medkernel.engine.knowledge;

/**
 * 知识版本替换时的可信分级裁决摘要。
 *
 * <p>OPT-07 B0 确定性顺序：可信分级优先，其次来源版本发布时间，最后适用域精确度。
 */
record ConflictArbitration(String summary, boolean lowAuthorityOverrideHighAuthority) {

    static ConflictArbitration between(KnowledgeAssetVersion oldActive, KnowledgeAssetVersion target) {
        return between(oldActive, target, null, null);
    }

    static ConflictArbitration between(KnowledgeAssetVersion oldActive, KnowledgeAssetVersion target,
            SourceVersion oldSourceVersion, SourceVersion targetSourceVersion) {
        if (oldActive == null || target == null || oldActive.authorityLevel() == null || target.authorityLevel() == null) {
            return new ConflictArbitration("可信分级裁决：旧版或新版缺少来源分级快照，本次不自动阻断，需由审核人按来源引用人工确认。", false);
        }
        SourceAuthorityLevel oldLevel = oldActive.authorityLevel();
        SourceAuthorityLevel targetLevel = target.authorityLevel();
        boolean lowOverHigh = targetLevel.isLowAuthority() && oldLevel.isHighAuthority();
        if (targetLevel.rank() < oldLevel.rank()) {
            return new ConflictArbitration(
                "可信分级裁决：新版来源分级 " + targetLevel.label() + " 高于旧版 " + oldLevel.label()
                    + "，默认采用新版；后续时效和适用域仅作解释补充。",
                false
            );
        }
        if (targetLevel.rank() > oldLevel.rank()) {
            String prefix = lowOverHigh ? "可信分级裁决：低阶来源覆盖高阶来源，" : "可信分级裁决：新版来源分级低于旧版，";
            return new ConflictArbitration(
                prefix + "新版 " + targetLevel.label() + " 低于旧版 " + oldLevel.label()
                    + "，必须保留显式理由和审核留痕；时效和适用域仅作解释补充。",
                lowOverHigh
            );
        }
        java.time.Instant oldTime = SourceEvidencePriority.evidenceTime(oldSourceVersion);
        java.time.Instant targetTime = SourceEvidencePriority.evidenceTime(targetSourceVersion);
        int recency = SourceEvidencePriority.compareRecency(targetTime, oldTime);
        if (recency > 0) {
            return new ConflictArbitration(
                "可信分级裁决：同为 " + targetLevel.label() + "，时效优先；新版来源发布时间 "
                    + SourceEvidencePriority.evidenceDate(targetTime) + " 晚于旧版 "
                    + SourceEvidencePriority.evidenceDate(oldTime) + "，默认采用新版。",
                false
            );
        }
        if (recency < 0) {
            return new ConflictArbitration(
                "可信分级裁决：同为 " + targetLevel.label() + "，新版来源发布时间 "
                    + SourceEvidencePriority.evidenceDate(targetTime) + " 早于旧版 "
                    + SourceEvidencePriority.evidenceDate(oldTime) + "，需保留审核理由后再替换。",
                false
            );
        }
        int oldScope = SourceEvidencePriority.scopeSpecificity(oldActive);
        int targetScope = SourceEvidencePriority.scopeSpecificity(target);
        if (targetScope > oldScope) {
            return new ConflictArbitration(
                "可信分级裁决：同级且同发布时间，适用域优先；新版 "
                    + scopeLabel(target) + " 比旧版 " + scopeLabel(oldActive) + " 更精确，默认采用新版。",
                false
            );
        }
        if (targetScope < oldScope) {
            return new ConflictArbitration(
                "可信分级裁决：同级且同发布时间，旧版适用域 " + scopeLabel(oldActive)
                    + " 比新版 " + scopeLabel(target) + " 更精确，需保留审核理由后再替换。",
                false
            );
        }
        return new ConflictArbitration(null, false);
    }

    boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }

    private static String scopeLabel(KnowledgeAssetVersion version) {
        return version.effectiveOrganizationScope() + "/" + version.effectiveApplicableScope();
    }
}
