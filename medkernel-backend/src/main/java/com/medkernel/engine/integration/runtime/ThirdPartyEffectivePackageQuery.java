package com.medkernel.engine.integration.runtime;

import java.time.Instant;

/**
 * 第三方有效知识包解析查询。
 */
public record ThirdPartyEffectivePackageQuery(
    String packageCode,
    String packageVersion,
    String targetOrgUnitId,
    String specialtyId,
    String scenarioCode,
    String careSetting,
    String cohort,
    String role,
    Instant effectiveAt
) {
}
