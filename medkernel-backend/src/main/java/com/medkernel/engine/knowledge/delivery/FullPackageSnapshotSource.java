package com.medkernel.engine.knowledge.delivery;

/**
 * 平台资源生产主链路向完整包导出器提供不可变快照的端口。
 *
 * <p>实现必须从关系库权威和受管资料库回读，不得从请求体、页面缓存或模型临时输出拼装。
 */
@FunctionalInterface
public interface FullPackageSnapshotSource {

    FullPackageSnapshot load(String platformReleaseIdentity);
}
