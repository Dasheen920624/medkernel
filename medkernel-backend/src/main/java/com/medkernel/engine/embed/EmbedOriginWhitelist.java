package com.medkernel.engine.embed;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 嵌入 Origin 来源域名允许清单实体。
 *
 * <p>用于存储和校验允许加载 MedKernel 嵌入式 iframe 或是执行 SDK 通信的来源域名允许清单。
 */
@Table("embed_origin_whitelist")
public record EmbedOriginWhitelist(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    String origin,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
