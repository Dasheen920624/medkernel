package com.medkernel.engine.sandbox.compare;

/** 新旧规则结果的结构化差异类型。 */
public enum SandboxRuleDifferenceType {
    NEW_HIT,
    NO_LONGER_HIT,
    SEVERITY_INCREASED,
    SEVERITY_DECREASED,
    ACTION_CHANGED,
    SOURCE_CHANGED,
    VERSION_CHANGED,
    ASSET_MISSING
}
