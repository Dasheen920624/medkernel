package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;

/**
 * AIK-STD-14 公域资料来源白名单。只有启用、已审批、许可允许且 robots 策略允许的来源可被抓取。
 */
@Table("mk_knowledge_acquisition_source")
public record KnowledgeAcquisitionSource(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("source_code") String sourceCode,
    @Column("domain") String domain,
    @Column("base_url") String baseUrl,
    @Column("source_type") SourceType sourceType,
    @Column("authority_level") SourceAuthorityLevel authorityLevel,
    @Column("authority_basis") String authorityBasis,
    @Column("title") String title,
    @Column("publisher") String publisher,
    @Column("license") String license,
    @Column("license_policy") AcquisitionLicensePolicy licensePolicy,
    @Column("robots_policy") AcquisitionRobotsPolicy robotsPolicy,
    @Column("enabled_flag") String enabledFlag,
    @Column("approved_by") String approvedBy,
    @Column("approved_at") Instant approvedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public boolean isEffective() {
        return "Y".equalsIgnoreCase(enabledFlag)
            && approvedBy != null && !approvedBy.isBlank()
            && approvedAt != null
            && licensePolicy != null && licensePolicy.isPermitted()
            && robotsPolicy != null && robotsPolicy.allowsFetch();
    }
}
