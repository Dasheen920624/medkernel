package com.medkernel.engine.knowledge.acquisition;

/**
 * 来源站点 robots / ToS 抓取策略裁决。
 */
public enum AcquisitionRobotsPolicy {
    /** robots / ToS 允许抓取。 */
    ALLOW_FETCH,
    /** robots / ToS 不明确，但经治理审批允许定向抓取。 */
    MANUAL_APPROVED,
    /** robots / ToS 明确禁止抓取。 */
    DISALLOW_FETCH;

    public boolean allowsFetch() {
        return this != DISALLOW_FETCH;
    }
}
