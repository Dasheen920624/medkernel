package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 路径模板发布前影响摘要。
 *
 * <p>仅使用当前关系库中的模板拓扑、关键时钟绑定和患者路径实例事实生成，用于 7 步流发布门禁。
 */
public record PathwayTemplateImpactResponse(
    String templateId,
    String analysisStatus,
    int affectedPatientPathways,
    int nodeCount,
    int edgeCount,
    int timedNodeCount,
    int terminalNodeCount,
    int canaryPercent,
    String impactDigest,
    List<String> releaseEvidence,
    String traceId
) {
    public PathwayTemplateImpactResponse {
        releaseEvidence = releaseEvidence == null ? List.of() : List.copyOf(releaseEvidence);
    }
}
