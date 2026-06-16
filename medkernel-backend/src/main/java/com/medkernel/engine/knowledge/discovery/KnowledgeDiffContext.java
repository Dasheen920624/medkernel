package com.medkernel.engine.knowledge.discovery;

/**
 * 知识差异检测上下文。
 *
 * @param runCode 探索运行编码
 * @param targetIdentityId 绑定的现行知识身份；为空表示全新知识候选
 */
public record KnowledgeDiffContext(
    String runCode,
    Long targetIdentityId
) {
}
