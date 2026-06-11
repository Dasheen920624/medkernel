package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 自然人在医疗组织中的任职关系。
 */
@Table("mk_person_appointment")
public record PersonAppointment(
    @Id @Column("appointment_id") String appointmentId,
    @Column("tenant_id") String tenantId,
    @Column("person_id") String personId,
    @Column("organization_id") String organizationId,
    @Column("department_id") String departmentId,
    @Column("ward_id") String wardId,
    @Column("appointment_type") AppointmentType appointmentType,
    @Column("position_title") String positionTitle,
    @Column("primary_flag") String primaryFlag,
    @Column("effective_from") Instant effectiveFrom,
    @Column("effective_to") Instant effectiveTo,
    @Column("status") AppointmentStatus status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) implements Persistable<String> {
    public boolean primary() {
        return "Y".equalsIgnoreCase(primaryFlag);
    }

    @Override
    public String getId() {
        return appointmentId;
    }

    @Override
    public boolean isNew() {
        return version != null && version == 1L && createdAt != null && createdAt.equals(updatedAt);
    }
}
