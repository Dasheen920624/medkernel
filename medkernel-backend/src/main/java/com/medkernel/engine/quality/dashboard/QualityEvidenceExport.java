package com.medkernel.engine.quality.dashboard;

import java.time.Instant;
import java.util.List;

/**
 * 质控驾驶舱证据导出载荷。
 *
 * <p>当前以 JSON 证据导出返回真实来源明细，后续页面可据此下载。
 */
public record QualityEvidenceExport(
    String exportId,
    Instant generatedAt,
    String scopeDigest,
    List<QualityDashboardDrilldownItem> items
) {}
