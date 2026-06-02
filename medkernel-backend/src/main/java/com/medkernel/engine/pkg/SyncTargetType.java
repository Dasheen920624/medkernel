package com.medkernel.engine.pkg;

/**
 * 同步发布目标类型枚举。
 */
public enum SyncTargetType {
    /** 业务数据库 */
    CLINICAL_DB,
    /** Dify */
    DIFY,
    /** 图数据库 (Neo4j) */
    GRAPH_DB,
    /** Redis 缓存 */
    REDIS
}
