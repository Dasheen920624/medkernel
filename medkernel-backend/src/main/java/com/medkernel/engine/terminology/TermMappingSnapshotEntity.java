package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 术语资产版本下的单条不可变映射快照。
 *
 * <p>每条快照对应一条 {@link TermMapping}。业务键列用于高效解析当前生效映射，
 * {@code mapping_snapshot} 保留版本创建时的完整不可变 JSON，确保映射后续改动或回滚时仍可复现。
 */
@Table("mk_term_mapping_snapshot")
public record TermMappingSnapshotEntity(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("version_id") String versionId,
    @Column("mapping_id") Long mappingId,
    @Column("local_term_id") Long localTermId,
    @Column("standard_term_id") Long standardTermId,
    @Column("source_system") String sourceSystem,
    @Column("local_code") String localCode,
    @Column("target_dictionary_key") String targetDictionaryKey,
    @Column("standard_code") String standardCode,
    @Column("category") String category,
    @Column("mapping_snapshot") String mappingSnapshot,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {

    public static TermMappingSnapshotEntity fromSnapshot(
            String tenantId,
            String versionId,
            Long persistedMappingId,
            TermMappingSnapshot snapshot,
            String mappingSnapshot,
            Instant now,
            String actor) {
        return new TermMappingSnapshotEntity(
            null,
            tenantId,
            versionId,
            persistedMappingId,
            snapshot.localTermId(),
            snapshot.standardTermId(),
            snapshot.sourceSystem(),
            snapshot.localCode(),
            snapshot.targetDictionaryKey(),
            snapshot.standardCode(),
            snapshot.category(),
            mappingSnapshot,
            now,
            actor
        );
    }
}
