package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户内自然人主数据。
 */
@Table("mk_identity_person")
public record Person(
    @Id @Column("person_id") String personId,
    @Column("tenant_id") String tenantId,
    @Column("employee_no") String employeeNo,
    @Column("display_name") String displayName,
    @Column("mobile_hint") String mobileHint,
    @Column("status") PersonStatus status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) implements Persistable<String> {

    @Override
    public String getId() {
        return personId;
    }

    @Override
    public boolean isNew() {
        return version != null && version == 1L && createdAt != null && createdAt.equals(updatedAt);
    }
}
