package com.medkernel.engine.llm.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 模型服务密钥登记或轮换请求。
 *
 * <p>凭据仅在本次请求体中传输，服务端立即加密保存；响应、日志与审计均不得回显。
 * 新增凭据时 {@code expectedVersion} 必须为空，轮换时必须与当前凭据版本一致。
 */
public record ModelProviderCredentialUpsertRequest(
    @NotBlank @Size(min = 8, max = 2048) String credential,
    @NotBlank @Size(min = 8, max = 500) String reason,
    @PositiveOrZero Long expectedVersion,
    boolean confirmedHighRisk
) {}
