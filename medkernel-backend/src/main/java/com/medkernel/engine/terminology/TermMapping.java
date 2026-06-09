package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 本地术语→标准术语的正式映射条目（候选确认后落库的主映射）。
 *
 * <p>租户隔离；同 (tenant_id, local_term_id, standard_term_id) 仅保留一条；
 * 状态字段 {@link TermMappingStatus} 反映生命周期（DRAFT/CONFIRMED/SUPERSEDED/ROLLED_BACK），
 * 仅 CONFIRMED 状态参与统一术语知识包构建。
 */
@Table("term_mapping")
public record TermMapping(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("local_term_id") Long localTermId,
    @Column("standard_term_id") Long standardTermId,
    @Column("source_system") String sourceSystem,
    @Column("category") TermCategory category,
    @Column("confidence") Double confidence,
    @Column("risk_level") TermRiskLevel riskLevel,
    @Column("status") TermMappingStatus status,
    @Column("evidence_text") String evidenceText,
    @Column("confirmed_by") String confirmedBy,
    @Column("confirmed_at") Instant confirmedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public static TermMapping imported(
            String tenantId,
            Long localTermId,
            Long standardTermId,
            String sourceSystem,
            String category,
            Double confidence,
            String riskLevel,
            String status,
            String evidenceText,
            String confirmedBy,
            Instant confirmedAt,
            Instant now,
            String actor) {
        return imported(
            null,
            tenantId,
            localTermId,
            standardTermId,
            sourceSystem,
            category,
            confidence,
            riskLevel,
            status,
            evidenceText,
            confirmedBy,
            confirmedAt,
            now,
            actor
        );
    }

    public static TermMapping imported(
            Long id,
            String tenantId,
            Long localTermId,
            Long standardTermId,
            String sourceSystem,
            String category,
            Double confidence,
            String riskLevel,
            String status,
            String evidenceText,
            String confirmedBy,
            Instant confirmedAt,
            Instant now,
            String actor) {
        return new TermMapping(
            id,
            tenantId,
            localTermId,
            standardTermId,
            sourceSystem,
            parseNullableEnum(TermCategory.class, category),
            confidence,
            parseRequiredEnum(TermRiskLevel.class, riskLevel, TermRiskLevel.MEDIUM),
            parseRequiredEnum(TermMappingStatus.class, status, TermMappingStatus.CONFIRMED),
            evidenceText,
            confirmedBy,
            confirmedAt,
            now,
            actor,
            now,
            actor
        );
    }

    public String categoryName() {
        return category == null ? null : category.name();
    }

    public String riskLevelName() {
        return riskLevel == null ? null : riskLevel.name();
    }

    public String statusName() {
        return status == null ? null : status.name();
    }

    public static void validateImportedEnums(String category, String riskLevel, String status) {
        parseNullableEnum(TermCategory.class, category);
        parseRequiredEnum(TermRiskLevel.class, riskLevel, TermRiskLevel.MEDIUM);
        parseRequiredEnum(TermMappingStatus.class, status, TermMappingStatus.CONFIRMED);
    }

    private static <E extends Enum<E>> E parseNullableEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(enumClass.getSimpleName() + "=" + value, e);
        }
    }

    private static <E extends Enum<E>> E parseRequiredEnum(Class<E> enumClass, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(enumClass.getSimpleName() + "=" + value, e);
        }
    }

    TermMapping confirmed(String userId, Instant now, String evidence, String sourceSystem, TermCategory category) {
        return new TermMapping(
            id, tenantId, localTermId, standardTermId,
            sourceSystem == null ? this.sourceSystem : sourceSystem,
            category == null ? this.category : category,
            confidence,
            riskLevel, TermMappingStatus.CONFIRMED, evidence, userId, now,
            createdAt, createdBy, now, userId
        );
    }
}
