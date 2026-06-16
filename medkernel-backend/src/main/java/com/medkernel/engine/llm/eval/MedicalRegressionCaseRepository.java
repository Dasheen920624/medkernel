package com.medkernel.engine.llm.eval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 医学回归基准集数据访问存储库（LLM-07）。
 */
@Repository
public interface MedicalRegressionCaseRepository extends CrudRepository<MedicalRegressionCase, Long> {

    List<MedicalRegressionCase> findByTenantIdAndCapabilityCodeAndEnabledFlag(
        String tenantId, String capabilityCode, String enabledFlag);

    Optional<MedicalRegressionCase> findByTenantIdAndCapabilityCodeAndCaseInput(
        String tenantId, String capabilityCode, String caseInput);
}
