package com.medkernel.engine.llm;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
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
    String updatedBy,
    @Transient boolean newEntity
) implements Persistable<String> {

    /**
     * 自数据库加载或在已知能力代码上重建的实体：视为已存在，保存走 UPDATE。
     *
     * <p>主键 {@code capabilityCode} 是业务自然键，新建与更新共用保存入口，
     * 须经 {@link Persistable} 由调用方按存在性显式声明新建语义，否则新增目录项会被误判为 UPDATE。
     */
    @PersistenceCreator
    public ModelCapabilityDefinition(
        String capabilityCode,
        String displayName,
        String description,
        String category,
        String enabledFlag,
        Integer sortOrder,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
    ) {
        this(capabilityCode, displayName, description, category, enabledFlag, sortOrder,
            createdAt, createdBy, updatedAt, updatedBy, false);
    }

    @Override
    public String getId() {
        return capabilityCode;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public boolean enabled() {
        return "Y".equalsIgnoreCase(enabledFlag);
    }
}
