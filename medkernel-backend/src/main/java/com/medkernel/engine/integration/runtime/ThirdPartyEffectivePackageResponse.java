package com.medkernel.engine.integration.runtime;

import java.time.Instant;

import com.medkernel.engine.pkg.EffectivePackageSnapshot;

/**
 * 第三方有效知识包响应。
 */
public record ThirdPartyEffectivePackageResponse(
    String contractVersion,
    Instant effectiveAt,
    String applicableScope,
    EffectivePackageSnapshot snapshot
) {
}
