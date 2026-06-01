package com.medkernel.engine.security.auth;

import java.time.Instant;

/**
 * 管理员发放的一次性密码重置 token；明文 token 仅返回一次。
 */
public record PasswordResetTokenResponse(String resetToken, Instant expiresAt) {}
