package com.medkernel.engine.knowledge.diagnosis;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 置信策略仓储：按租户 + scopeKey 读取；运行时未覆盖回退平台主租户 t-1 的 DEFAULT。
 */
@Repository
public interface DiagnosisConfidencePolicyRepository extends ListCrudRepository<DiagnosisConfidencePolicy, Long> {

    Optional<DiagnosisConfidencePolicy> findByTenantIdAndScopeKey(String tenantId, String scopeKey);
}
