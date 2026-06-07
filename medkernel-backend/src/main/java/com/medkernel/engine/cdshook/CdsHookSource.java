package com.medkernel.engine.cdshook;

/**
 * CDS Hooks 卡片来源。
 */
public record CdsHookSource(
    String label,
    String url,
    String evidenceLevel
) {}
