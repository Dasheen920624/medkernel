package com.medkernel.shared.audit.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.medkernel.shared.audit.AuditEvent;

/**
 * 审计持久化失败时的本地降级存储。
 *
 * <p>主审计表不可用时，业务链路仍不能被审计故障拖垮；本组件把已脱敏的
 * {@link AuditEvent} 追加到本地 JSONL 文件，供运维后续补偿导入和排障。
 */
@Component
public class AuditFallbackStore {

    private final Path fallbackPath;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditFallbackStore(
            @Value("${medkernel.audit.fallback.path:}") String configuredPath,
            ObjectMapper objectMapper) {
        this(resolvePath(configuredPath), objectMapper);
    }

    AuditFallbackStore(Path fallbackPath, ObjectMapper objectMapper) {
        this.fallbackPath = fallbackPath;
        this.objectMapper = objectMapper.copy();
    }

    public synchronized Path store(AuditEvent event, RuntimeException failure) throws IOException {
        Path parent = fallbackPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FallbackAuditRecord record = FallbackAuditRecord.from(event, failure);
        String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
        Files.writeString(fallbackPath, line,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE,
            java.nio.file.StandardOpenOption.APPEND);
        return fallbackPath;
    }

    public Path fallbackPath() {
        return fallbackPath;
    }

    private static Path resolvePath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir"), "var", "audit-fallback", "audit-fallback.jsonl")
            .toAbsolutePath()
            .normalize();
    }

    private record FallbackAuditRecord(
        String eventId,
        String traceId,
        String occurredAt,
        String actorUserId,
        String actorRoles,
        String action,
        String resourceType,
        String resourceId,
        String tenantId,
        String orgPath,
        String environmentKey,
        String outcome,
        String errorCode,
        String payloadDigest,
        String beforeSnapshot,
        String afterSnapshot,
        String failureType,
        String failureMessage,
        String writtenAt
    ) {

        static FallbackAuditRecord from(AuditEvent event, RuntimeException failure) {
            String tenantId = event.orgScope() == null ? null : event.orgScope().tenantId();
            return new FallbackAuditRecord(
                event.id(),
                event.traceId(),
                event.occurredAt() == null ? null : event.occurredAt().toString(),
                event.actorUserId(),
                event.actorRoles(),
                event.action() == null ? null : event.action().name(),
                event.resourceType(),
                event.resourceId(),
                tenantId,
                event.orgPath(),
                event.environmentKey(),
                event.outcome(),
                event.errorCode(),
                event.payloadDigest(),
                event.beforeSnapshot(),
                event.afterSnapshot(),
                failure.getClass().getName(),
                failure.getMessage(),
                Instant.now().toString());
        }
    }
}
