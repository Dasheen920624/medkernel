package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 来源文件登记（5 方言一致）。
 *
 * <p>对应详细规范 §8.3 "权威来源"：指南、说明书、行业标准、政策、院内制度等。
 * 每条来源文件可有多个版本（{@link SourceVersion}）；版本下可有多个引用锚点（{@link SourceFragment}）。
 */
@Table("source_document")
public record SourceDocument(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("source_code") String sourceCode,
    @Column("source_type") SourceType sourceType,
    @Column("authority_level") SourceAuthorityLevel authorityLevel,
    @Column("authority_basis") String authorityBasis,
    @Column("title") String title,
    @Column("publisher") String publisher,
    @Column("license") String license,
    @Column("language") String language,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
