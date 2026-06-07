package com.medkernel.engine.contract;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * SYS-02 领域事件 schema 目录。
 *
 * <p>目录用字符串记录事件类，避免为契约索引引入额外模块依赖。
 * JSON 文件是对外交付契约；本目录提供运行时和测试侧的版本索引。
 */
public final class DomainEventSchemaCatalog {
    private static final List<DomainEventSchema> SCHEMAS = List.of(
        schema("clinical-event.v1", 1,
            "com.medkernel.engine.context.ClinicalEvent",
            "docs/contracts/events/clinical-event.v1.json"),
        schema("clinical-event-processed.v1", 1,
            "com.medkernel.engine.context.ClinicalEventProcessedEvent",
            "docs/contracts/events/clinical-event-processed.v1.json"),
        schema("compliance-audit-event.v1", 1,
            "com.medkernel.compliance.audit.AuditEvent",
            "docs/contracts/events/compliance-audit-event.v1.json"),
        schema("followup-event.v1", 1,
            "com.medkernel.engine.followup.FollowupEvent",
            "docs/contracts/events/followup-event.v1.json"),
        schema("integration-outbound-queued-event.v1", 1,
            "com.medkernel.engine.integration.service.IntegrationOutboundQueuedEvent",
            "docs/contracts/events/integration-outbound-queued-event.v1.json"),
        schema("shared-audit-event.v1", 1,
            "com.medkernel.shared.audit.AuditEvent",
            "docs/contracts/events/shared-audit-event.v1.json")
    ).stream()
        .sorted(Comparator.comparing(DomainEventSchema::schemaId))
        .toList();

    private DomainEventSchemaCatalog() {
    }

    public static List<DomainEventSchema> schemas() {
        return SCHEMAS;
    }

    public static Optional<DomainEventSchema> schemaOfRecord(String recordClassName) {
        return SCHEMAS.stream()
            .filter(schema -> schema.recordClassName().equals(recordClassName))
            .findFirst();
    }

    private static DomainEventSchema schema(String schemaId, int version, String recordClassName, String contractFile) {
        return new DomainEventSchema(schemaId, version, recordClassName, contractFile);
    }
}
