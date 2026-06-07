package com.medkernel.compliance.identitybinding;

import java.time.Instant;

/**
 * 外部身份绑定安全响应，不返回身份原文或摘要。
 */
public record IdentityBindingResponse(
    String bindingId,
    String userId,
    String providerType,
    String subjectHint,
    String status,
    long version,
    Instant createdAt,
    Instant updatedAt
) {

    static IdentityBindingResponse from(IdentityBinding binding) {
        return new IdentityBindingResponse(
            binding.bindingId(),
            binding.userId(),
            binding.providerType(),
            binding.subjectHint(),
            binding.status(),
            binding.version(),
            binding.createdAt(),
            binding.updatedAt());
    }
}
