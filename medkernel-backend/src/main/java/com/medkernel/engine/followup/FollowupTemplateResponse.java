package com.medkernel.engine.followup;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 随访模板响应。
 */
public record FollowupTemplateResponse(
    String templateId,
    String templateCode,
    Integer versionNo,
    String name,
    String description,
    String organizationScope,
    String applicableScope,
    List<FollowupTemplateTaskInput> tasks,
    String questionnaireDefinition,
    String abnormalActionDefinition,
    String sourceRef,
    String assetVersionId,
    AssetVersionStatus assetStatus,
    String contentHash,
    Instant updatedAt,
    String traceId
) {
    public FollowupTemplateResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
