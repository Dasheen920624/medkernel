package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 专病指标绑定仓库。
 *
 * <p>保存路径知识包、路径模板、节点和质控指标之间的绑定关系。
 */
@Repository
public interface SpecialtyMetricBindingRepository extends ListCrudRepository<SpecialtyMetricBinding, Long> {

    /**
     * 按业务 ID 和租户查询单条指标绑定。
     */
    Optional<SpecialtyMetricBinding> findByBindingIdAndTenantId(String bindingId, String tenantId);

    /**
     * 查询路径模板下所有指标绑定，并按节点编码升序排列。
     */
    List<SpecialtyMetricBinding> findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
        String templateId, String tenantId);
}
