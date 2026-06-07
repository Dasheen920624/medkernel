package com.medkernel.compliance.user;

import java.time.Instant;
import java.util.List;

/**
 * 用户管理列表摘要，不暴露租户内部键和认证秘密。
 */
public record ComplianceUserSummary(
    String userId,
    String displayName,
    String username,
    boolean credentialManaged,
    String status,
    boolean mustChangePwd,
    List<ComplianceUserRole> roles,
    Instant createdAt
) {
}
