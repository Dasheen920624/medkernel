package com.medkernel.engine.context;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准临床上下文资源（Canonical Resource）仓储接口。
 *
 * <p>提供针对标准结构化临床数据（如诊断、就诊、医嘱等）的持久化与检索支持，
 * 配合快照与 Trace ID 实现端到端可信数据审计留痕。
 */
@Repository
public interface CanonicalResourceRepository extends ListCrudRepository<CanonicalResource, Long> {

    List<CanonicalResource> findBySnapshotIdOrderBySeqNoAsc(String snapshotId);

    List<CanonicalResource> findBySnapshotIdAndTenantIdOrderBySeqNoAsc(String snapshotId, String tenantId);

    List<CanonicalResource> findByTraceIdOrderBySeqNoAsc(String traceId);
}
