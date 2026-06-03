package com.medkernel.engine.integration.fhir;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * FHIR 运行门面响应，外层直接返回 FHIR JSON。
 */
public record FhirFacadeResponse(
    HttpStatus status,
    JsonNode body
) {}
