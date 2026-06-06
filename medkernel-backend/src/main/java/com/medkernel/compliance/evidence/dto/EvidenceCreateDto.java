package com.medkernel.compliance.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 医疗合规可信存证证据快照创建 DTO Record。
 */
public record EvidenceCreateDto(
    @NotBlank(message = "证据 ID 不能为空")
    @Size(max = 64, message = "证据 ID 长度不能超过 64")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "证据 ID 只能包含字母、数字、点、下划线和连字符")
    String evidenceId,

    @Size(max = 128, message = "traceId 长度不能超过 128")
    String traceId,

    @NotBlank(message = "证据资产类型不能为空")
    @Size(max = 64, message = "证据资产类型长度不能超过 64")
    String evidenceType,

    @NotBlank(message = "审计动作类型不能为空")
    @Size(max = 32, message = "审计动作类型长度不能超过 32")
    String action,

    @NotBlank(message = "业务关联实体对象类型不能为空")
    @Size(max = 128, message = "业务关联实体对象类型长度不能超过 128")
    String subjectType,

    @NotBlank(message = "业务关联实体对象 ID 不能为空")
    @Size(max = 64, message = "业务关联实体对象 ID 长度不能超过 64")
    String subjectId,

    @NotBlank(message = "证据简要描述不能为空")
    @Size(max = 512, message = "证据简要描述长度不能超过 512")
    String evidenceSummary,

    @NotBlank(message = "业务数据快照报文不能为空")
    String payloadSnapshot
) {}
