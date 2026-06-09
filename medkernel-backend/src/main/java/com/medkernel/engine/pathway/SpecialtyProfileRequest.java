package com.medkernel.engine.pathway;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

/**
 * 专病画像请求片段。
 *
 * <p>用于创建路径知识包时声明分型、风险分层、准入条件、退出条件和随访计划摘要。
 */
public record SpecialtyProfileRequest(
    @NotBlank String profileCode,
    @NotBlank String name,
    JsonNode stratification,
    JsonNode entryCriteria,
    JsonNode exitCriteria,
    JsonNode followupPlan
) {}
