package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 术语映射包内单条映射快照（构包时定格）。
 *
 * <p>每条 item 对应一条 {@link TermMapping}。业务键列用于高效解析当前生效映射，
 * {@code mapping_snapshot} 保留构包时的完整不可变 JSON，确保映射后续改动或回滚时仍可复现。
 */
@Table("term_mapping_package_item")
public record TermMappingPackageItem(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("package_id") Long packageId,
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

    public static TermMappingPackageItem fromSnapshot(
            String tenantId,
            Long packageId,
            Long persistedMappingId,
            TermMappingSnapshot snapshot,
            String mappingSnapshot,
            Instant now,
            String actor) {
        return new TermMappingPackageItem(
            null,
            tenantId,
            packageId,
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
