package com.medkernel.engine.pathway;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建路径阶段里程碑的请求片段。
 *
 * <p>用于定义路径模板中的阶段、里程碑编码、天序和预期完成点。
 */
public record PathwayMilestoneRequest(
    @NotBlank String phaseCode,
    @NotBlank String phaseName,
    @NotBlank String milestoneCode,
    @NotBlank String name,
    @Min(0) Integer dayOffset,
    @Min(0) Integer expectedOffsetMinutes,
    JsonNode achievementCriteria,
    @Min(0) Integer sortOrder
) {}
