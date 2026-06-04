package com.medkernel.engine.safety;

import java.util.List;

/**
 * 临床安全红线目录响应。
 */
public record ClinicalRedlineCatalogResponse(
    ClinicalRedlineContentStatus contentStatus,
    List<ClinicalRedlineCategory> categories,
    List<ClinicalRedlineResponse> redlines,
    String traceId
) {
}
