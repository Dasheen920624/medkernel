package com.medkernel.engine.terminology;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语映射不可变快照持久化仓库；快照直接绑定术语资产版本。
 */
@Repository
public interface TermMappingSnapshotRepository extends ListCrudRepository<TermMappingSnapshotEntity, Long> {
    List<TermMappingSnapshotEntity> findByTenantIdAndVersionId(String tenantId, String versionId);

    /**
     * 查询指定机构生效版本锁定的术语映射快照。
     */
    @Query("""
        SELECT DISTINCT
               item.mapping_id,
               item.standard_term_id,
               item.standard_code,
               version.version_no,
               CASE
                   WHEN version.org_path = :facilityOrgPath THEN 'FACILITY'
                   WHEN version.org_path = :regionOrgPath THEN 'REGION'
                   ELSE 'TENANT'
               END AS scope_level
          FROM mk_term_mapping_snapshot item
          JOIN clinical_runtime_release runtime_release
            ON runtime_release.release_id = :runtimeReleaseId
           AND runtime_release.tenant_id = :tenantId
          JOIN clinical_runtime_release_item runtime_item
            ON runtime_item.release_id = runtime_release.release_id
           AND runtime_item.source_tenant_id = item.tenant_id
           AND runtime_item.asset_type = 'TERMINOLOGY'
           AND runtime_item.entry_state = 'ACTIVE'
           AND runtime_item.version_id = item.version_id
          JOIN mk_version_asset_version version
            ON version.version_id = runtime_item.version_id
           AND version.tenant_id = runtime_item.source_tenant_id
           AND version.asset_type = 'TERMINOLOGY'
           AND version.asset_identity = runtime_item.asset_identity
           AND version.version_no = runtime_item.version_no
           AND version.content_hash = runtime_item.content_hash
         WHERE 1 = 1
           AND version.status = 'PUBLISHED'
           AND item.local_code = :localCode
           AND (:sourceSystem IS NULL OR item.source_system = :sourceSystem)
           AND (:targetDictionaryKey IS NULL OR item.target_dictionary_key = :targetDictionaryKey)
           AND (:category IS NULL OR item.category = :category)
           AND version.org_path IN (:organizationScopes)
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByAnchor(
        String tenantId,
        String runtimeReleaseId,
        List<String> organizationScopes,
        String regionOrgPath,
        String facilityOrgPath,
        String sourceSystem,
        String localCode,
        String targetDictionaryKey,
        String category
    );

    /**
     * 按标准字典编码查询指定机构生效版本中的映射快照，供覆盖率验收使用。
     */
    @Query("""
        SELECT DISTINCT
               item.mapping_id,
               item.standard_term_id,
               item.standard_code,
               version.version_no,
               CASE
                   WHEN version.org_path = :facilityOrgPath THEN 'FACILITY'
                   WHEN version.org_path = :regionOrgPath THEN 'REGION'
                   ELSE 'TENANT'
               END AS scope_level
          FROM mk_term_mapping_snapshot item
          JOIN clinical_runtime_release runtime_release
            ON runtime_release.release_id = :runtimeReleaseId
           AND runtime_release.tenant_id = :tenantId
          JOIN clinical_runtime_release_item runtime_item
            ON runtime_item.release_id = runtime_release.release_id
           AND runtime_item.source_tenant_id = item.tenant_id
           AND runtime_item.asset_type = 'TERMINOLOGY'
           AND runtime_item.entry_state = 'ACTIVE'
           AND runtime_item.version_id = item.version_id
          JOIN mk_version_asset_version version
            ON version.version_id = runtime_item.version_id
           AND version.tenant_id = runtime_item.source_tenant_id
           AND version.asset_type = 'TERMINOLOGY'
           AND version.asset_identity = runtime_item.asset_identity
           AND version.version_no = runtime_item.version_no
           AND version.content_hash = runtime_item.content_hash
         WHERE 1 = 1
           AND version.status = 'PUBLISHED'
           AND item.target_dictionary_key = :targetDictionaryKey
           AND item.standard_code = :standardCode
           AND version.org_path IN (:organizationScopes)
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByStandardCode(
        String tenantId,
        String runtimeReleaseId,
        List<String> organizationScopes,
        String regionOrgPath,
        String facilityOrgPath,
        String targetDictionaryKey,
        String standardCode
    );
}
