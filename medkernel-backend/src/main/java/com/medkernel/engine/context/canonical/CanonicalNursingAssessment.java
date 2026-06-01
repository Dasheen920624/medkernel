package com.medkernel.engine.context.canonical;

import java.time.Instant;

import com.medkernel.engine.context.QualityStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 标准护理评估。对齐 SYS-01 NursingAssessment。
 */
public record CanonicalNursingAssessment(
    @NotBlank String assessmentId,
    @NotBlank String assessmentType,
    String riskLevel,
    String status,
    String sourceSystem,
    String sourceRecordId,
    String mappedVersion,
    Instant eventTime,
    Instant receivedTime,
    @NotNull QualityStatus qualityStatus
) {}
