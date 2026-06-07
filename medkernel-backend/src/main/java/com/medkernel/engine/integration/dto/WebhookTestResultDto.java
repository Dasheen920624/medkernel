package com.medkernel.engine.integration.dto;

/**
 * 外部 Webhook 签名预览结果。
 *
 * <p>只证明本地签名材料可生成，不代表外部回调地址网络可达。
 * 响应不回显共享密钥和原始业务载荷。
 */
public record WebhookTestResultDto(
    String webhookId,
    String callbackUrl,
    Long timestamp,
    String signature,
    String status,
    String connectionStatus,
    String message
) {
}
