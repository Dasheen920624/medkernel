package com.medkernel.engine.pkg;

import com.medkernel.engine.integration.domain.IntegrationAdapter;

/**
 * 知识包外部同步发布 Port 接口。
 *
 * <p>统一复用集成适配器目录向院内外系统执行真实同步发布。
 */
public interface PackageSyncPort {

    /**
     * 判断适配器协议是否存在真实连接器。
     */
    boolean supports(IntegrationAdapter adapter);

    /**
     * 将指定的发布计划和目标通道进行同步。
     *
     * @param tenantId 租户 ID
     * @param plan     发布计划
     * @param adapter  统一集成适配器
     * @param snapshot 解析后的机构有效包快照
     * @return 真实同步发布执行后的数字签名/哈希存证
     * @throws PackageSyncNotConnectedException 未接入真实同步发布通道，必须诚实返回 NOT_SYNCED
     * @throws Exception 同步发布异常
     */
    String sync(
        String tenantId,
        ReleasePlan plan,
        IntegrationAdapter adapter,
        EffectivePackageSnapshot snapshot
    ) throws Exception;
}
