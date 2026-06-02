package com.medkernel.engine.pkg;

import org.springframework.stereotype.Component;

/**
 * 默认知识包同步诚实降级适配器。
 *
 * <p>根据 B0 离线基线原则，若无外部三方同步发布（如 Neo4j / Dify 集成）实现，
 * 本组件默认兜底装配，明确返回 NOT_SYNCED，不伪造同步成功或证据摘要。
 */
@Component
public class LenientPackageSyncAdapter implements PackageSyncPort {

    @Override
    public String sync(String tenantId, ReleasePlan plan, SyncTarget target) {
        throw new PackageSyncNotConnectedException(
            "NOT_SYNCED：同步目标 " + target.targetId() + " 未配置真实同步适配器，未执行同步发布");
    }
}
