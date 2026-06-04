package com.medkernel.engine.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 安全撤回请求体。
 *
 * <p>{@code scope} 为调用方声明的撤回范围说明，真实生效范围仍以知识版本自身的组织 / 临床适用域为准。
 */
public record SafetyWithdrawalRequest(
    @NotNull Long identityId,
    @NotNull Long versionId,
    @NotBlank @Size(max = 500) String reason,
    @Size(max = 200) String scope
) {
    public SafetyWithdrawalRequest(Long identityId, Long versionId, String reason) {
        this(identityId, versionId, reason, null);
    }
}
