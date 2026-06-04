package com.medkernel.engine.knowledge.diagnosis;

/** 单个候选诊断的命中统计：主要/次要命中数、必需项总数/命中数、是否命中排除项。 */
public record DiagnosisMatchStats(
    int majorHits, int minorHits, int requiredTotal, int requiredHit, boolean hitExclusion) {}
