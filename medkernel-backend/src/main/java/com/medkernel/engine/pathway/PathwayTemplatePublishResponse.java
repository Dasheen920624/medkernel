package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 路径模板发布响应。
 *
 * <p>返回模板 ID、发布后状态、7 步流位置、影响摘要和 traceId，用于确认发布门禁后的状态变更。
 */
public record PathwayTemplatePublishResponse(
    String templateId,
    PathwayTemplateStatus status,
    String releaseStep,
    int canaryPercent,
    String impactDigest,
    String analysisStatus,
    List<String> releaseEvidence,
    String traceId
) {
    public PathwayTemplatePublishResponse {
        releaseEvidence = releaseEvidence == null ? List.of() : List.copyOf(releaseEvidence);
    }
}
