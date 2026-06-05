package com.medkernel.engine.evaluation;

import jakarta.validation.constraints.NotBlank;

/**
 * 整改豁免请求。
 *
 * <p>豁免必须提供业务理由和审批引用，证据引用用于补充关联附件或会议纪要。
 */
public record RectificationWaiveRequest(
    @NotBlank String reason,
    @NotBlank String approvalRef,
    String evidenceRef
) {}
