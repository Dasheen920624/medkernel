package com.medkernel.engine.terminology;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语映射不可变快照持久化仓库；写多读少，主要用于构包写入与运行时解析。
 */
@Repository
public interface TermMappingSnapshotRepository extends ListCrudRepository<TermMappingSnapshotEntity, Long> {
    List<TermMappingSnapshotEntity> findByTenantIdAndPackageItemId(String tenantId, String packageItemId);

    /**
     * 查询当前组织编码锚点可见的全量激活映射快照；灰度、草稿和已下线版本均不参与运行时。
     */
    @Query("""
        SELECT DISTINCT
               item.mapping_id,
               item.standard_term_id,
               item.standard_code,
               CASE
                   WHEN version.org_path = 'DEPARTMENT:' || :departmentScope THEN 'DEPARTMENT'
                   WHEN version.org_path = 'CAMPUS:' || :campusScope THEN 'CAMPUS'
                   WHEN version.org_path = 'FACILITY:' || :facilityScope THEN 'FACILITY'
                   WHEN version.org_path = 'REGION:' || :regionScope THEN 'REGION'
                   ELSE 'TENANT'
               END AS scope_level
          FROM mk_term_mapping_snapshot item
          JOIN package_item package_item
            ON package_item.item_id = item.package_item_id
           AND package_item.tenant_id = item.tenant_id
          JOIN knowledge_package kp
            ON kp.package_id = package_item.package_id
           AND kp.tenant_id = package_item.tenant_id
          JOIN mk_version_asset_version version
            ON version.tenant_id = kp.tenant_id
           AND version.asset_type = 'PACKAGE'
           AND version.asset_identity = kp.package_code
           AND version.version_no = kp.package_version
         WHERE item.tenant_id = :tenantId
           AND kp.status = 'ACTIVE'
           AND version.status = 'PUBLISHED'
           AND item.local_code = :localCode
           AND (:sourceSystem IS NULL OR item.source_system = :sourceSystem)
           AND (:targetDictionaryKey IS NULL OR item.target_dictionary_key = :targetDictionaryKey)
           AND (:category IS NULL OR item.category = :category)
           AND (
                version.org_path = 'TENANT:' || :tenantScope
             OR version.org_path = 'REGION:' || :regionScope
             OR version.org_path = 'FACILITY:' || :facilityScope
             OR version.org_path = 'CAMPUS:' || :campusScope
             OR version.org_path = 'DEPARTMENT:' || :departmentScope
           )
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByAnchor(
        String tenantId,
        String tenantScope,
        String regionScope,
        String facilityScope,
        String campusScope,
        String departmentScope,
        String sourceSystem,
        String localCode,
        String targetDictionaryKey,
        String category
    );

    /**
     * 按标准字典编码查询当前组织可见的全量激活映射快照，供覆盖率验收使用。
     */
    @Query("""
        SELECT DISTINCT
               item.mapping_id,
               item.standard_term_id,
               item.standard_code,
               CASE
                   WHEN version.org_path = 'DEPARTMENT:' || :departmentScope THEN 'DEPARTMENT'
                   WHEN version.org_path = 'CAMPUS:' || :campusScope THEN 'CAMPUS'
                   WHEN version.org_path = 'FACILITY:' || :facilityScope THEN 'FACILITY'
                   WHEN version.org_path = 'REGION:' || :regionScope THEN 'REGION'
                   ELSE 'TENANT'
               END AS scope_level
          FROM mk_term_mapping_snapshot item
          JOIN package_item package_item
            ON package_item.item_id = item.package_item_id
           AND package_item.tenant_id = item.tenant_id
          JOIN knowledge_package kp
            ON kp.package_id = package_item.package_id
           AND kp.tenant_id = package_item.tenant_id
          JOIN mk_version_asset_version version
            ON version.tenant_id = kp.tenant_id
           AND version.asset_type = 'PACKAGE'
           AND version.asset_identity = kp.package_code
           AND version.version_no = kp.package_version
         WHERE item.tenant_id = :tenantId
           AND kp.status = 'ACTIVE'
           AND version.status = 'PUBLISHED'
           AND item.target_dictionary_key = :targetDictionaryKey
           AND item.standard_code = :standardCode
           AND (
                version.org_path = 'TENANT:' || :tenantScope
             OR version.org_path = 'REGION:' || :regionScope
             OR version.org_path = 'FACILITY:' || :facilityScope
             OR version.org_path = 'CAMPUS:' || :campusScope
             OR version.org_path = 'DEPARTMENT:' || :departmentScope
           )
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByStandardCode(
        String tenantId,
        String tenantScope,
        String regionScope,
        String facilityScope,
        String campusScope,
        String departmentScope,
        String targetDictionaryKey,
        String standardCode
    );
}
