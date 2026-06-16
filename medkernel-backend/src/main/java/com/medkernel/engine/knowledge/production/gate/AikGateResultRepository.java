package com.medkernel.engine.knowledge.production.gate;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

/**
 * 候选安全门禁结果仓储（AIK-STD-05）。
 *
 * <p>append-only：仅写入与按 job 回溯查询门禁结果，不更新不删除（审计轨迹不可改写，铁律 #1）。
 */
public interface AikGateResultRepository extends ListCrudRepository<AikGateResult, Long> {

    /** 按租户 + job 列门禁结果（可审计回溯），按 id 升序保留评估顺序。 */
    List<AikGateResult> findByTenantIdAndJobCodeOrderByIdAsc(String tenantId, String jobCode);
}
