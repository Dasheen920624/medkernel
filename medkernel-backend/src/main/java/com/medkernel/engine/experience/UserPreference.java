package com.medkernel.engine.experience;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 用户体验偏好持久化实体。
 *
 * <p>偏好按租户、用户和键隔离；当前用于主题模式与通知偏好，后续体验偏好仍复用同一表族。
 *
 * <p>主键 {@code userPrefId} 由业务侧指派（非空字符串），Spring Data JDBC 默认据此判定为
 * 已存在实体而发出 UPDATE；空库首次保存时无对应行，真实 PostgreSQL 抛
 * {@code IncorrectUpdateSemanticsDataAccessException}。因此实现 {@link Persistable}，
 * 由瞬时标志 {@code newEntity} 显式声明新建语义，确保首次保存走 INSERT。
 */
@Table("mk_experience_user_pref")
public record UserPreference(
    @Id
    @Column("user_pref_id")
    String userPrefId,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("pref_key") String prefKey,
    @Column("pref_value") String prefValue,
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
    public UserPreference(
        String userPrefId,
        String tenantId,
        String userId,
        String prefKey,
        String prefValue,
        long version,
        String status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
    ) {
        this(userPrefId, tenantId, userId, prefKey, prefValue, version, status,
            createdAt, createdBy, updatedAt, updatedBy, false);
    }

    @Override
    public String getId() {
        return userPrefId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    UserPreference updateValue(String value, String actor, Instant now) {
        return new UserPreference(
            userPrefId,
            tenantId,
            userId,
            prefKey,
            value,
            version + 1,
            "ACTIVE",
            createdAt,
            createdBy,
            now,
            actor
        );
    }

    /**
     * 新建一条体验偏好（INSERT 语义），供主题与通知设置等偏好键复用。
     */
    public static UserPreference create(
        String id,
        String tenantId,
        String userId,
        String prefKey,
        String value,
        Instant now
    ) {
        return new UserPreference(
            id,
            tenantId,
            userId,
            prefKey,
            value,
            1,
            "ACTIVE",
            now,
            userId,
            now,
            userId,
            true
        );
    }
}
