package com.medkernel.engine.experience;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 页面保存视图持久化实体。
 *
 * <p>视图只保存筛选、列配置、排序等非临床上下文快照，按租户和用户物理隔离。
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
    @Column("updated_by") String updatedBy
) {

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
            userId
        );
    }

    private static String flag(boolean value) {
        return value ? "Y" : "N";
    }
}
