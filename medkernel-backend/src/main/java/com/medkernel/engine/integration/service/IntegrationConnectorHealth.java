package com.medkernel.engine.integration.service;

/**
 * 连接器真实探活结果。
 */
public record IntegrationConnectorHealth(String status, long rttMs, String message) {
}
