package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * 发现标准化默认实现：TERM 对照尚未接入时一律返回空（未映射），保持确定性、不做字符近似兜底。
 *
 * <p>这是<b>诚实降级</b>的集成挂点而非废码：架构上端口可替换，编排链路已完整且有测试覆盖；
 * 未映射发现如实进响应 {@code unmappedFindings}、不伪造命中。接入后改为查询
 * {@code standard_term} ACTIVE + {@code term_mapping} CONFIRMED 的确定性映射。
 */
@Component
public class DefaultFindingNormalizationPort implements FindingNormalizationPort {

    @Override
    public Optional<String> normalize(String tenantId, CanonicalResourceType type, String localCode, String codeSystem) {
        return Optional.empty();
    }
}
