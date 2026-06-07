package com.medkernel.engine.terminology;

/**
 * 术语映射包内的完整不可变快照。
 */
public record TermMappingSnapshot(
    Long mappingId,
    Long localTermId,
    Long standardTermId,
    String sourceSystem,
    String localCode,
    String targetDictionaryKey,
    String standardCode,
    String category,
    Double confidence,
    String riskLevel,
    String status,
    String evidenceText,
    String confirmedBy,
    String confirmedAt
) {

    static TermMappingSnapshot from(TermMapping mapping, LocalTerm localTerm, StandardTerm standardTerm) {
        return new TermMappingSnapshot(
            mapping.id(),
            mapping.localTermId(),
            mapping.standardTermId(),
            mapping.sourceSystem(),
            localTerm.localCode(),
            standardTerm.standardSystem(),
            standardTerm.termCode(),
            mapping.categoryName(),
            mapping.confidence(),
            mapping.riskLevelName(),
            mapping.statusName(),
            mapping.evidenceText(),
            mapping.confirmedBy(),
            mapping.confirmedAt() == null ? null : mapping.confirmedAt().toString()
        );
    }

    public TermMappingSnapshot withPersistenceIds(
            Long persistedMappingId,
            Long persistedLocalTermId,
            Long persistedStandardTermId) {
        return new TermMappingSnapshot(
            persistedMappingId,
            persistedLocalTermId,
            persistedStandardTermId,
            sourceSystem,
            localCode,
            targetDictionaryKey,
            standardCode,
            category,
            confidence,
            riskLevel,
            status,
            evidenceText,
            confirmedBy,
            confirmedAt
        );
    }
}
