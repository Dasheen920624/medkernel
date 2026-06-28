package com.medkernel.engine.tenant;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户定制品牌信息实体。
 *
 * <p>用于 GA-SVC-PILOT-01 业务服务组合的医院个性化品牌界面定制。
 */
@Table("tenant_branding")
public record Branding(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("hospital_name") String hospitalName,
    @Column("logo_url") String logoUrl,
    @Column("theme_color") String themeColor,
    @Column("evidence_details_enabled") Boolean evidenceDetailsEnabled,
    @Column("custom_branding_json") String customBrandingJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
