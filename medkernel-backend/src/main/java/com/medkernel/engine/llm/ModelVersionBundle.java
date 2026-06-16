package com.medkernel.engine.llm;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * prompt/tool/model 版本包。
 *
 * <p>只保存版本号与内容指纹，不保存提示词正文或工具密钥，便于审计导出且避免敏感内容扩散。
 */
@Table("mk_llm_model_version_bundle")
public record ModelVersionBundle(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("prompt_version") String promptVersion,
    @Column("prompt_hash") String promptHash,
    @Column("tool_version") String toolVersion,
    @Column("tool_hash") String toolHash,
    @Column("model_version") String modelVersion,
    @Column("model_hash") String modelHash,
    String status,
    @Column("effective_at") Instant effectiveAt,
    @Column("retired_at") Instant retiredAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
