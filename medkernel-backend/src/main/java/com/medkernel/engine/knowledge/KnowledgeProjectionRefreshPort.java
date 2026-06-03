package com.medkernel.engine.knowledge;

/**
 * 知识资产发布后的投影刷新端口。
 *
 * <p>关系库仍是唯一权威源；实现方只能刷新或标记派生投影，不得把图/搜索作为业务事实来源。
 */
public interface KnowledgeProjectionRefreshPort {

    KnowledgeProjectionRefreshResult refreshPublishedVersion(
        String tenantId,
        Long identityId,
        Long versionId,
        String requestedBy,
        String traceId
    );
}
