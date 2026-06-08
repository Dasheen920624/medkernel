package com.medkernel.engine.terminology;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语映射包条目持久化仓库；写多读少，主要用于构包写入与回查。
 */
@Repository
public interface TermMappingPackageItemRepository extends ListCrudRepository<TermMappingPackageItem, Long> {
    List<TermMappingPackageItem> findByTenantIdAndPackageId(String tenantId, Long packageId);

    /**
     * 查询当前组织编码锚点可见的全量激活映射快照；灰度、草稿和已下线版本均不参与运行时。
     */
    @Query("""
        SELECT DISTINCT
               item.mapping_id,
               item.standard_term_id,
               item.standard_code,
               package.scope_level
          FROM term_mapping_package_item item
          JOIN term_mapping_package package
            ON package.id = item.package_id
           AND package.tenant_id = item.tenant_id
          JOIN mk_version_asset_version version
            ON version.tenant_id = package.tenant_id
           AND version.asset_type = 'TERMINOLOGY'
           AND version.asset_identity = package.package_code
           AND version.version_no = package.package_version
         WHERE item.tenant_id = :tenantId
           AND package.status = 'PUBLISHED'
           AND version.status = 'PUBLISHED'
           AND item.local_code = :localCode
           AND (:sourceSystem IS NULL OR item.source_system = :sourceSystem)
           AND (:targetDictionaryKey IS NULL OR item.target_dictionary_key = :targetDictionaryKey)
           AND (:category IS NULL OR item.category = :category)
           AND (
                (package.scope_level = 'TENANT' AND package.scope_code = :tenantScope)
             OR (package.scope_level = 'GROUP' AND package.scope_code = :groupScope)
             OR (package.scope_level = 'HOSPITAL' AND package.scope_code = :hospitalScope)
             OR (package.scope_level = 'CAMPUS' AND package.scope_code = :campusScope)
             OR (package.scope_level = 'SITE' AND package.scope_code = :siteScope)
             OR (package.scope_level = 'DEPARTMENT' AND package.scope_code = :departmentScope)
           )
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByAnchor(
        String tenantId,
        String tenantScope,
        String groupScope,
        String hospitalScope,
        String campusScope,
        String siteScope,
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
               package.scope_level
          FROM term_mapping_package_item item
          JOIN term_mapping_package package
            ON package.id = item.package_id
           AND package.tenant_id = item.tenant_id
          JOIN mk_version_asset_version version
            ON version.tenant_id = package.tenant_id
           AND version.asset_type = 'TERMINOLOGY'
           AND version.asset_identity = package.package_code
           AND version.version_no = package.package_version
         WHERE item.tenant_id = :tenantId
           AND package.status = 'PUBLISHED'
           AND version.status = 'PUBLISHED'
           AND item.target_dictionary_key = :targetDictionaryKey
           AND item.standard_code = :standardCode
           AND (
                (package.scope_level = 'TENANT' AND package.scope_code = :tenantScope)
             OR (package.scope_level = 'GROUP' AND package.scope_code = :groupScope)
             OR (package.scope_level = 'HOSPITAL' AND package.scope_code = :hospitalScope)
             OR (package.scope_level = 'CAMPUS' AND package.scope_code = :campusScope)
             OR (package.scope_level = 'SITE' AND package.scope_code = :siteScope)
             OR (package.scope_level = 'DEPARTMENT' AND package.scope_code = :departmentScope)
           )
         ORDER BY item.mapping_id
        """)
    List<EffectiveTermMappingCandidate> findEffectiveByStandardCode(
        String tenantId,
        String tenantScope,
        String groupScope,
        String hospitalScope,
        String campusScope,
        String siteScope,
        String departmentScope,
        String targetDictionaryKey,
        String standardCode
    );
}
