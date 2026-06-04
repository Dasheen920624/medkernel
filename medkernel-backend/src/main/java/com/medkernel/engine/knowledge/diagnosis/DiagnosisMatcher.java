package com.medkernel.engine.knowledge.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 诊断命中核心：发现集 + 一组诊断标准 → 候选证据 + 置信分级。
 *
 * <p>确定性、可复现（同输入同标准同策略结果一致）；按 finding_term_code 命中。
 * value_constraint / temporal_constraint 的求值是<b>后续阶段挂点</b>（接 RuleDslEvaluator 的 between/unit_compare/temporal）；
 * Spec 1（Plan A+B）命中到编码级，这两个约束字段已落库但暂不求值。
 */
@Component
public class DiagnosisMatcher {

    private final DiagnosisConfidenceEvaluator evaluator;

    public DiagnosisMatcher(DiagnosisConfidenceEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public DiagnosisMatchResult match(Set<String> findings, List<DiagnosisCriterion> criteria,
                                      DiagnosisConfidencePolicy policy) {
        List<String> supporting = new ArrayList<>();
        List<String> refuting = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        int majorHits = 0;
        int minorHits = 0;
        int requiredTotal = 0;
        int requiredHit = 0;
        boolean hitExclusion = false;

        for (DiagnosisCriterion c : criteria) {
            boolean present = findings.contains(c.findingTermCode());
            switch (c.direction()) {
                case REQUIRED -> {
                    requiredTotal++;
                    if (present) {
                        requiredHit++;
                        supporting.add(c.findingTermCode());
                        if (c.weight() == DiagnosisWeight.MAJOR) {
                            majorHits++;
                        } else {
                            minorHits++;
                        }
                    } else {
                        missingRequired.add(c.findingTermCode());
                    }
                }
                case SUPPORTING -> {
                    if (present) {
                        supporting.add(c.findingTermCode());
                        if (c.weight() == DiagnosisWeight.MAJOR) {
                            majorHits++;
                        } else {
                            minorHits++;
                        }
                    }
                }
                case REFUTING -> {
                    if (present) {
                        refuting.add(c.findingTermCode());
                    }
                }
                case EXCLUSION -> {
                    if (present) {
                        hitExclusion = true;
                        refuting.add(c.findingTermCode());
                    }
                }
            }
        }
        var stats = new DiagnosisMatchStats(majorHits, minorHits, requiredTotal, requiredHit, hitExclusion);
        return new DiagnosisMatchResult(evaluator.evaluate(stats, policy),
            List.copyOf(supporting), List.copyOf(refuting), List.copyOf(missingRequired), hitExclusion);
    }
}
