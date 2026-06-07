package com.medkernel.engine.llm;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台模型能力目录定义。
 *
 * <p>目录是能力代码、中文名称、业务分类和启停状态的关系库权威源。租户策略只能引用已启用目录项，
 * 不允许业务服务或前端另行维护能力清单。
 */
@Table("model_capability_definition")
public record ModelCapabilityDefinition(
    @Id
    @Column("capability_code")
    String capabilityCode,
    @Column("display_name")
    String displayName,
    String description,
    String category,
    @Column("enabled_flag")
    String enabledFlag,
    @Column("sort_order")
    Integer sortOrder,
    @Column("created_at")
    Instant createdAt,
    @Column("created_by")
    String createdBy,
    @Column("updated_at")
    Instant updatedAt,
    @Column("updated_by")
    String updatedBy
) {
    public boolean enabled() {
        return "Y".equalsIgnoreCase(enabledFlag);
    }
}
