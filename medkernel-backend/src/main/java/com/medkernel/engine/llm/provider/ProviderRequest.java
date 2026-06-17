package com.medkernel.engine.llm.provider;

/**
 * provider 推理请求（LLM-08）。出域内容已经 {@code ModelEgressGuard} 最小化+脱敏（B2）。
 */
public record ProviderRequest(String capabilityCode, String prompt, int timeoutMs) {}
