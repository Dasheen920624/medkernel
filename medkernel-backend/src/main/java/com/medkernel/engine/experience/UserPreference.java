package com.medkernel.engine.experience;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 用户体验偏好持久化实体。
 *
 * <p>偏好按租户、用户和键隔离；当前用于主题模式，后续体验偏好仍复用同一表族。
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
    @Column("updated_by") String updatedBy
) {

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

    static UserPreference create(
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
            userId
        );
    }
}
