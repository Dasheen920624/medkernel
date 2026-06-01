package com.medkernel.engine.security.bootstrap;

/**
 * 首次部署 MFA 绑定入参；label 仅用于前端显示，服务端不信任其安全含义。
 */
public record BootstrapMfaRequest(String label) {}
