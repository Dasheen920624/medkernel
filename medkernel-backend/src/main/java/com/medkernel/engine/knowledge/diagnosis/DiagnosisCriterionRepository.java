package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 诊断标准仓储：按租户 + 诊断版本读取（id 升序），删除收窄到租户。 */
@Repository
public interface DiagnosisCriterionRepository extends ListCrudRepository<DiagnosisCriterion, Long> {

    @Query("SELECT * FROM mk_diagnosis_criterion WHERE tenant_id = :tenantId "
         + "AND diagnosis_version_id = :versionId ORDER BY id ASC")
    List<DiagnosisCriterion> findByTenantIdAndDiagnosisVersionId(String tenantId, Long versionId);

    @Modifying
    @Query("DELETE FROM mk_diagnosis_criterion WHERE tenant_id = :tenantId AND id = :id")
    void deleteByTenantIdAndId(String tenantId, Long id);
}
