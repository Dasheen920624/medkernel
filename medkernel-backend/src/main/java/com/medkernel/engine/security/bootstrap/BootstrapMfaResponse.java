package com.medkernel.engine.security.bootstrap;

/**
 * MFA 绑定结果：恢复码仅返回一次，数据库只保存摘要。
 */
public record BootstrapMfaResponse(boolean mfaBound, String recoveryCode) {}
