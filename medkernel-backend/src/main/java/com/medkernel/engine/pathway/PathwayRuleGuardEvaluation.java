package com.medkernel.engine.pathway;

import com.medkernel.engine.release.ReleaseSourceLayer;

/**
 * 路径分支引用规则后的确定性求值结果。
 *
 * @param matched        规则是否命中
 * @param ruleCode       规则稳定业务编码
 * @param ruleId         实际生效规则 ID
 * @param versionId      实际生效规则版本 ID
 * @param versionNo      实际生效规则版本号
 * @param runtimeReleaseId 医院运行修订 ID
 * @param sourceTenantId   规则正文来源租户
 * @param sourceLayer      规则实际来源层
 */
public record PathwayRuleGuardEvaluation(
    boolean matched,
    String ruleCode,
    String ruleId,
    String versionId,
    int versionNo,
    String runtimeReleaseId,
    String sourceTenantId,
    ReleaseSourceLayer sourceLayer
) {
}
