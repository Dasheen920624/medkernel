package com.medkernel.engine.integration.fhir;

/**
 * FHIR search 运行门面命令。
 */
public record FhirFacadeSearchCommand(
    FhirVersion version,
    String resourceType,
    String adapterId,
    String sourceIp,
    String patient
) {}
