package com.medkernel.engine.pathway;

/**
 * 路径变异处置决策。
 *
 * <p>以结构化方式表达变异登记后是暂停观察、再入径，还是终止当前患者路径。
 */
public enum VarianceResolutionDecision {
    HOLD,
    REENTER,
    TERMINATE
}
