package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

/**
 * 首次部署 init token 检查入参。
 */
public record BootstrapStartRequest(@NotBlank String token) {}
