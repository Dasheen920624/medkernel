package com.medkernel.engine.knowledge.diagnosis;

import org.springframework.stereotype.Component;

/**
 * 确定性置信分级：仅输出等级，绝不输出百分比概率（守 NMPA 边界）。阈值取自可配置策略，不硬编码。
 */
@Component
public class DiagnosisConfidenceEvaluator {

    public DiagnosisConfidence evaluate(DiagnosisMatchStats s, DiagnosisConfidencePolicy p) {
        if (s.hitExclusion()) {
            return DiagnosisConfidence.EXCLUDE;
        }
        boolean requiredSatisfied = !p.requireAllRequired() || s.requiredHit() >= s.requiredTotal();
        if (!requiredSatisfied) {
            return DiagnosisConfidence.WEAK;
        }
        if (s.majorHits() >= p.strongMinMajor()) {
            return DiagnosisConfidence.STRONG;
        }
        if (s.majorHits() + s.minorHits() >= p.moderateMinHits()) {
            return DiagnosisConfidence.MODERATE;
        }
        return DiagnosisConfidence.WEAK;
    }
}
