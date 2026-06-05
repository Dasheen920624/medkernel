package com.medkernel.engine.evaluation;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 整改派发请求。
 *
 * <p>质控办将未派发问题分派到责任科室和可选责任人，必须给出截止时间。
 */
public record RectificationDispatchRequest(
    @NotBlank String findingId,
    @NotBlank String responsibleDepartmentId,
    String assigneeUserId,
    @NotNull Instant dueAt
) {}
