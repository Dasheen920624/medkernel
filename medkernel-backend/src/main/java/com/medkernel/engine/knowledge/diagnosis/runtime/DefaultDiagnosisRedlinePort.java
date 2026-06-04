package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 红线合流默认实现：当前返回空、不阻断主链路、不伪造红线（诚实降级的集成挂点）。
 *
 * <p>OPT-04 临床安全红线目录已合入 main；将"红线 rule_definition 经 RuleDslEvaluator 对患者结构化上下文求值
 * → 命中转 RedlineHit"的接线作为后续（红线条件按 medications/conditions 等结构求值，非仅发现编码集，
 * 需扩展端口入参）。编排侧红线置顶排序逻辑已就绪且有测试。
 */
@Component
public class DefaultDiagnosisRedlinePort implements DiagnosisRedlinePort {

    @Override
    public List<RedlineHit> check(String tenantId, Set<String> normalizedFindings) {
        return List.of();
    }
}
