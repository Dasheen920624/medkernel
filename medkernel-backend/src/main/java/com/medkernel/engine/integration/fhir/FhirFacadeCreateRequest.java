package com.medkernel.engine.integration.fhir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/**
 * FHIR 运行门面 create 请求 DTO。
 */
public record FhirFacadeCreateRequest(
    @NotNull JsonNode resource
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FhirFacadeCreateRequest {
    }
}
