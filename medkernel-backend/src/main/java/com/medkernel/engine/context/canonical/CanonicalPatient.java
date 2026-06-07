package com.medkernel.engine.context.canonical;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.medkernel.engine.context.QualityStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 标准患者主索引。对齐 detail spec §7.4 Patient。
 */
public record CanonicalPatient(
    @NotBlank String mpi,
    @NotBlank String name,
    LocalDate birthDate,
    String gender,
    List<String> specialPopulations,
    String sourceSystem,
    String sourceRecordId,
    String mappedVersion,
    Instant eventTime,
    Instant receivedTime,
    @NotNull QualityStatus qualityStatus
) {}
