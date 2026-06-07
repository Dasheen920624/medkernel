package com.medkernel.engine.integration.service;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.medkernel.engine.integration.domain.IntegrationAdapter;

/**
 * 第三方适配器真实连接器。
 */
public interface IntegrationConnector {

    boolean supports(IntegrationAdapter adapter);

    IntegrationConnectorValidation validate(IntegrationAdapter adapter);

    IntegrationConnectorHealth checkHealth(IntegrationAdapter adapter);

    IntegrationDeliveryResult deliver(
        IntegrationAdapter adapter,
        JsonNode payload,
        String messageId,
        String traceId,
        Map<String, String> runtimeHeaders
    );
}
