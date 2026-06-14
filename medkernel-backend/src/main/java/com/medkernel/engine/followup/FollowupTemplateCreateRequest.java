package com.medkernel.engine.followup;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建随访模板草稿请求。
 */
public record FollowupTemplateCreateRequest(
    @NotBlank @Size(max = 128) String templateCode,
    @Positive Integer versionNo,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotBlank @Size(max = 1000) String organizationScope,
    @NotBlank @Size(max = 512) String applicableScope,
    @NotEmpty List<@Valid FollowupTemplateTaskInput> tasks,
    @NotBlank String questionnaireDefinition,
    @NotBlank String abnormalActionDefinition,
    @NotBlank @Size(max = 1000) String sourceRef
) {
    public FollowupTemplateCreateRequest {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
