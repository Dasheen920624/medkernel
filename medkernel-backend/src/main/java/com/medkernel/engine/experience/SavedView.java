package com.medkernel.engine.experience;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 页面保存视图持久化实体。
 *
 * <p>视图只保存筛选、列配置、排序等非临床上下文快照，按租户和用户物理隔离。
 *
 * <p>主键 {@code savedViewId} 由业务侧指派，须实现 {@link Persistable} 显式声明新建语义，
 * 否则空库首次保存视图会被 Spring Data JDBC 误判为 UPDATE 而失败（同 UserPreference）。
 */
@Table("mk_experience_saved_view")
public record SavedView(
    @Id
    @Column("saved_view_id")
    String savedViewId,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("page_key") String pageKey,
    @Column("view_name") String viewName,
    @Column("definition_json") String definitionJson,
    @Column("default_flag") String defaultFlag,
    @Column("version") long version,
    @Column("status") String status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Transient boolean newEntity
) implements Persistable<String> {

    /**
     * 自数据库加载或在已知主键上重建的实体：视为已存在，保存走 UPDATE。
     */
    @PersistenceCreator
    public SavedView(
        String savedViewId,
        String tenantId,
        String userId,
        String pageKey,
        String viewName,
        String definitionJson,
        String defaultFlag,
        long version,
        String status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
    ) {
        this(savedViewId, tenantId, userId, pageKey, viewName, definitionJson, defaultFlag,
            version, status, createdAt, createdBy, updatedAt, updatedBy, false);
    }

    @Override
    public String getId() {
        return savedViewId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    boolean isDefaultView() {
        return "Y".equalsIgnoreCase(defaultFlag);
    }

    SavedView updatedBy(SavedViewRequest request, String actor, Instant now) {
        return new SavedView(
            savedViewId,
            tenantId,
            userId,
            request.pageKey(),
            request.viewName(),
            request.definitionJson(),
            flag(request.defaultView()),
            version + 1,
            "ACTIVE",
            createdAt,
            createdBy,
            now,
            actor
        );
    }

    static SavedView create(String id, String tenantId, String userId, SavedViewRequest request, Instant now) {
        return new SavedView(
            id,
            tenantId,
            userId,
            request.pageKey(),
            request.viewName(),
            request.definitionJson(),
            flag(request.defaultView()),
            1,
            "ACTIVE",
            now,
            userId,
            now,
            userId,
            true
        );
    }

    private static String flag(boolean value) {
        return value ? "Y" : "N";
    }
}
