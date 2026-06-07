package com.medkernel.engine.integration.service;

/**
 * 连接器配置校验结果。
 */
public record IntegrationConnectorValidation(boolean valid, String reason) {

    public static IntegrationConnectorValidation success() {
        return new IntegrationConnectorValidation(true, "配置有效");
    }

    public static IntegrationConnectorValidation invalid(String reason) {
        return new IntegrationConnectorValidation(false, reason);
    }
}
