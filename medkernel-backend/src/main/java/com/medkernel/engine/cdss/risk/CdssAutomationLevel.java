package com.medkernel.engine.cdss.risk;

import com.medkernel.engine.recommendation.RecommendationInterruptLevel;

/**
 * CDSS 自动化程度：用于 OPT-03 风险分级矩阵。
 */
public enum CdssAutomationLevel {
    INFORM_ONLY,
    INTERRUPTIVE,
    AUTOMATED;

    public static CdssAutomationLevel fromInterruptLevel(RecommendationInterruptLevel interruptLevel) {
        if (interruptLevel == RecommendationInterruptLevel.WEAK_INTERRUPTIVE
                || interruptLevel == RecommendationInterruptLevel.STRONG_INTERRUPTIVE) {
            return INTERRUPTIVE;
        }
        return INFORM_ONLY;
    }
}
