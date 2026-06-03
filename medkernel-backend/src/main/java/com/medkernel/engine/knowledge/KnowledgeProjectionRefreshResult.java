package com.medkernel.engine.knowledge;

/**
 * 知识版本发布后派生投影刷新的同步证据。
 *
 * <p>字段只暴露同步任务标识与状态字符串，避免知识域反向依赖投影实现包。
 */
public record KnowledgeProjectionRefreshResult(
    String graphSyncId,
    String graphStatus,
    String searchSyncId,
    String searchStatus
) {
}
