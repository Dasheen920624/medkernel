package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 人员批量导入任务。 */
@Table("mk_identity_person_import_job")
public record PersonnelImportJob(
    @Id @Column("job_id") String jobId,
    @Column("tenant_id") String tenantId,
    @Column("file_name") String fileName,
    @Column("file_digest") String fileDigest,
    @Column("status") PersonnelImportStatus status,
    @Column("total_rows") Integer totalRows,
    @Column("valid_rows") Integer validRows,
    @Column("conflict_rows") Integer conflictRows,
    @Column("success_rows") Integer successRows,
    @Column("failure_rows") Integer failureRows,
    @Column("committed_at") Instant committedAt,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) implements Persistable<String> {

    @Override
    public String getId() {
        return jobId;
    }

    @Override
    public boolean isNew() {
        return version != null && version == 1L && createdAt != null && createdAt.equals(updatedAt);
    }
}
