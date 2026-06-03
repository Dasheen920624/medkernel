package com.medkernel.engine.integration.fhir;

/**
 * FHIR read 运行门面命令。
 */
public record FhirFacadeReadCommand(
    FhirVersion version,
    String resourceType,
    String id,
    String adapterId,
    String sourceIp
) {}
