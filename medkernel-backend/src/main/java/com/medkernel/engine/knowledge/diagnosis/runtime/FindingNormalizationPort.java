package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.Optional;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * 发现标准化端口：本地编码 → TERM-01 标准编码；未映射返回空（不猜不补，守确定性候选原则）。
 *
 * <p>{@code codeSystem} 可空（仅 CONDITION 资源携带显式编码体系；其余资源由 {@code type} 隐含体系）。
 */
@FunctionalInterface
public interface FindingNormalizationPort {
    Optional<String> normalize(String tenantId, CanonicalResourceType type, String localCode, String codeSystem);
}
