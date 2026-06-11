package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 人员批量导入逐行校验和处理结果。 */
@Table("mk_person_import_row")
public record PersonnelImportRow(
    @Id @Column("row_id") String rowId,
    @Column("job_id") String jobId,
    @Column("tenant_id") String tenantId,
    @Column("row_no") Integer rowNo,
    @Column("employee_no") String employeeNo,
    @Column("display_name") String displayName,
    @Column("organization_code") String organizationCode,
    @Column("department_code") String departmentCode,
    @Column("ward_code") String wardCode,
    @Column("appointment_type") String appointmentType,
    @Column("position_title") String positionTitle,
    @Column("login_name") String loginName,
    @Column("role_code") String roleCode,
    @Column("identity_provider") String identityProvider,
    @Column("external_subject_digest") String externalSubjectDigest,
    @Column("external_subject_hint") String externalSubjectHint,
    @Column("action") String action,
    @Column("status") String status,
    @Column("error_message") String errorMessage,
    @Column("result_person_id") String resultPersonId,
    @Column("result_user_id") String resultUserId,
    @Column("created_at") Instant createdAt
) implements Persistable<String> {

    @Override
    public String getId() {
        return rowId;
    }

    @Override
    public boolean isNew() {
        return "VALID".equals(status) || "INVALID".equals(status);
    }
}
