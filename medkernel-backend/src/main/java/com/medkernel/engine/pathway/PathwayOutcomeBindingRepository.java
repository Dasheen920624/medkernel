package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径结局指标绑定仓库。
 *
 * <p>按模板读取结局指标绑定，用于发布影响分析、患者路径详情和包资产内容。
 */
@Repository
public interface PathwayOutcomeBindingRepository extends ListCrudRepository<PathwayOutcomeBinding, Long> {

    Optional<PathwayOutcomeBinding> findByBindingIdAndTenantId(String bindingId, String tenantId);

    List<PathwayOutcomeBinding> findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
        String templateId, String tenantId);
}
