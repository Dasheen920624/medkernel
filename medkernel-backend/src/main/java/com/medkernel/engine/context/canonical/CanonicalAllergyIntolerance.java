package com.medkernel.engine.context.canonical;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.context.QualityStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 结构化过敏/不良反应资源，用于药物-过敏规则与路径守卫的真实 canonical 输入。
 */
public record CanonicalAllergyIntolerance(
    @NotBlank String allergyIntoleranceId,
    @NotBlank String code,
    String codeSystem,
    @NotBlank String substance,
    String category,
    String criticality,
    List<String> reactions,
    String clinicalStatus,
    String verificationStatus,
    String sourceSystem,
    String sourceRecordId,
    String mappedVersion,
    Instant onsetTime,
    Instant receivedTime,
    @NotNull QualityStatus qualityStatus
) {

    public CanonicalAllergyIntolerance {
        reactions = reactions == null ? List.of() : List.copyOf(reactions);
    }
}
