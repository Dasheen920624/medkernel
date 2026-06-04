package com.medkernel.engine.knowledge.diagnosis.runtime;

/** 红线命中：要置顶且不可被疲劳抑制的高危项（致命病 / 危急值 / 严重 DDI）。 */
public record RedlineHit(String identityCode, String severity, String reason) {}
