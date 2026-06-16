package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
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

    long countByTenantIdAndStatus(String tenantId, OverrideTemplateStatus status);

    @Query("""
        SELECT * FROM mk_version_override_template
        WHERE tenant_id = :tenantId
          AND status = :status
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<OverrideTemplate> pageByTenantIdAndStatus(
        String tenantId,
        OverrideTemplateStatus status,
        int offset,
        int limit
    );
}
