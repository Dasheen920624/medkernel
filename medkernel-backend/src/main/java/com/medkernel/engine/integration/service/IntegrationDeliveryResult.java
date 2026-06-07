package com.medkernel.engine.integration.service;

/**
 * 第三方消息投递结果。
 */
public record IntegrationDeliveryResult(boolean delivered, boolean connected, String errorMessage) {

    public static IntegrationDeliveryResult success() {
        return new IntegrationDeliveryResult(true, true, null);
    }

    public static IntegrationDeliveryResult failed(boolean connected, String errorMessage) {
        return new IntegrationDeliveryResult(false, connected, errorMessage);
    }
}
