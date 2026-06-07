package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 试点首发配置包模板仓储。
 */
@Repository
public interface PilotPackageTemplateRepository extends ListCrudRepository<PilotPackageTemplate, String> {

    List<PilotPackageTemplate> findByTenantIdAndStatusOrderByTemplateCodeAsc(
        String tenantId, PilotPackageTemplateStatus status);

    Optional<PilotPackageTemplate> findByTenantIdAndTemplateCodeAndStatus(
        String tenantId, String templateCode, PilotPackageTemplateStatus status);
}
