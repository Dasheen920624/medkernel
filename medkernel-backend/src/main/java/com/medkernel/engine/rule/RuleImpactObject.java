package com.medkernel.engine.rule;

/**
 * 规则影响分析中的受影响对象。
 *
 * <p>只承载数据库中可真实定位的对象，不填造患者、路径或同步目标。
 */
public record RuleImpactObject(
    String objectType,
    String objectId,
    String displayName,
    String impactReason
) {}
