package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 覆盖模板仓库。
 */
@Repository
public interface OverrideTemplateRepository extends ListCrudRepository<OverrideTemplate, Long> {

    Optional<OverrideTemplate> findByTemplateIdAndTenantId(String templateId, String tenantId);

    Optional<OverrideTemplate> findByTenantIdAndTemplateName(String tenantId, String templateName);

    List<OverrideTemplate> findByTenantIdAndStatusOrderByUpdatedAtDesc(
        String tenantId,
        OverrideTemplateStatus status
    );
}
