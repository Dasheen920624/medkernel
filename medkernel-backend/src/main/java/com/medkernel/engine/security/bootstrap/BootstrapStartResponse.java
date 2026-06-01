package com.medkernel.engine.security.bootstrap;

import java.time.Instant;

/**
 * 首次部署 init token 检查结果；只返回状态，不签发业务登录态。
 */
public record BootstrapStartResponse(boolean valid, Instant expiresAt) {}
