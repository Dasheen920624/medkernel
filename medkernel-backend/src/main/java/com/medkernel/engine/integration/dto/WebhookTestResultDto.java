package com.medkernel.engine.integration.dto;

/**
 * 外部 Webhook 签名测试推导结果契约 DTO Record。
 *
 * <p>提供类型安全与标准契约的签名测试返回值，杜绝使用裸 Map 进行非类型安全传递。
 */
public record WebhookTestResultDto(
    /**
     * Webhook 全局唯一业务标识
     */
    String webhookId,

    /**
     * 第三方回调目标 URL 地址
     */
    String callbackUrl,

    /**
     * 强随机生成的 128 位数字签名共享密钥 (SecretKey)
     */
    String secretKey,

    /**
     * 签名时采用的防回放 Unix 时间戳 (秒)
     */
    Long timestamp,

    /**
     * 串联签名原始报文 (timestamp + "." + payload)
     */
    String payloadSigned,

    /**
     * 经 HMAC-SHA256 算法推导算得的最终安全签名值
     */
    String signature,

    /**
     * 校验测试连通状态 (如 SUCCESS)
     */
    String status
) {}
