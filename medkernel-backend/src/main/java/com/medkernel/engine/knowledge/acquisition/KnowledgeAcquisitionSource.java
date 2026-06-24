package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;

/**
 * AIK-STD-14 公域资料来源允许清单。只有启用、许可允许且 robots 策略允许的来源可被抓取。
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
    @Column("schedule_enabled_flag") String scheduleEnabledFlag,
    @Column("schedule_interval_minutes") Integer scheduleIntervalMinutes,
    @Column("next_check_at") Instant nextCheckAt,
    @Column("last_check_at") Instant lastCheckAt,
    @Column("default_format") DocumentFormat defaultFormat,
    @Column("generation_plan_json") String generationPlanJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Version @Column("lock_version") Long version
) {
    public boolean isEffective() {
        return "Y".equalsIgnoreCase(enabledFlag)
            && licensePolicy != null && licensePolicy.isPermitted()
            && robotsPolicy != null && robotsPolicy.allowsFetch();
    }

    public boolean isScheduleReady() {
        return isEffective()
            && "Y".equalsIgnoreCase(scheduleEnabledFlag)
            && scheduleIntervalMinutes != null
            && scheduleIntervalMinutes > 0
            && baseUrl != null && !baseUrl.isBlank()
            && defaultFormat != null;
    }
}
