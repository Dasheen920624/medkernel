package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 自然人与系统用户主体之间的稳定关联。
 */
@Table("mk_identity_person_account")
public record PersonAccountLink(
    @Id @Column("link_id") String linkId,
    @Column("tenant_id") String tenantId,
    @Column("person_id") String personId,
    @Column("user_id") String userId,
    @Column("status") String status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) implements Persistable<String> {

    @Override
    public String getId() {
        return linkId;
    }

    @Override
    public boolean isNew() {
        return version != null && version == 1L && createdAt != null && createdAt.equals(updatedAt);
    }
}
