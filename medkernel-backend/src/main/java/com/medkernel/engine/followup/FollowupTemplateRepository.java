package com.medkernel.engine.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 随访模板仓库。
 */
@Repository
public interface FollowupTemplateRepository extends ListCrudRepository<FollowupTemplate, Long> {

    Optional<FollowupTemplate> findByTemplateIdAndTenantId(String templateId, String tenantId);

    Optional<FollowupTemplate> findByTenantIdAndTemplateCodeAndVersionNo(
        String tenantId,
        String templateCode,
        Integer versionNo
    );

    List<FollowupTemplate> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
