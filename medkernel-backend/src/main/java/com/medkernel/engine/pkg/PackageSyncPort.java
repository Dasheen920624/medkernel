package com.medkernel.engine.pkg;

/**
 * 知识包外部同步投影 Port 接口。
 *
 * <p>承担向 Neo4j、Dify、Clinical DB 等目标执行物理同步的职责。
 */
public interface PackageSyncPort {

    /**
     * 将指定的发布计划和投影通道进行同步。
     *
     * @param tenantId 租户 ID
     * @param plan     发布计划
     * @param target   投影通道
     * @return 真实物理同步执行后的数字签名/哈希存证
     * @throws PackageSyncNotConnectedException 未接入真实物理同步通道，必须诚实返回 NOT_SYNCED
     * @throws Exception 物理同步异常
     */
    String sync(String tenantId, ReleasePlan plan, SyncTarget target) throws Exception;
}
