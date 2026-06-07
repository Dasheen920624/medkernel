package com.medkernel.engine.context.canonical;

import java.time.Instant;

import com.medkernel.engine.context.QualityStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 标准就诊。对齐 detail spec §7.4 Encounter。
 */
public record CanonicalEncounter(
    @NotBlank String encounterId,
    @NotBlank String encounterType,
    @NotNull Instant admissionTime,
    Instant dischargeTime,
    String departmentId,
    String attendingDoctorId,
    String bedId,
    String sourceSystem,
    String sourceRecordId,
    String mappedVersion,
    Instant eventTime,
    Instant receivedTime,
    @NotNull QualityStatus qualityStatus
) {
    public CanonicalEncounter {
        ClinicalSetting.requireCanonical(encounterType);
    }
}
