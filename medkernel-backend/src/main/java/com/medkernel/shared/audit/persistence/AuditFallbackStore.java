package com.medkernel.shared.audit.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 审计持久化失败时的本地降级存储。
 *
 * <p>主审计表不可用时，业务链路仍不能被审计故障拖垮；本组件把已脱敏的
 * {@link AuditEvent} 追加到本地 JSONL 文件，供运维后续补偿导入和排障。
 */
@Component
public class AuditFallbackStore {

    private final AuditFallbackProperties properties;
    private final SystemConfigService configService;
    private final Path fixedFallbackPath;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditFallbackStore(
            AuditFallbackProperties properties,
            SystemConfigService configService,
            ObjectMapper objectMapper) {
        this(properties, configService, objectMapper, null);
    }

    AuditFallbackStore(Path fallbackPath, ObjectMapper objectMapper) {
        this(new AuditFallbackProperties(fallbackPath.toString()), null, objectMapper, fallbackPath);
    }

    private AuditFallbackStore(AuditFallbackProperties properties,
                               SystemConfigService configService,
                               ObjectMapper objectMapper,
                               Path fixedFallbackPath) {
        this.properties = properties;
        this.configService = configService;
        this.objectMapper = objectMapper.copy();
        this.fixedFallbackPath = fixedFallbackPath;
    }

    public synchronized Path store(AuditEvent event, RuntimeException failure) throws IOException {
        Path path = fallbackPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FallbackAuditRecord record = FallbackAuditRecord.from(event, failure);
        String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
        Files.writeString(path, line,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE,
            java.nio.file.StandardOpenOption.APPEND);
        return path;
    }

    public Path fallbackPath() {
        if (fixedFallbackPath != null) {
            return fixedFallbackPath;
        }
        String configuredPath = configService == null
            ? properties.pathOrDefault()
            : configService.runtimeAuditFallbackPath(properties);
        return resolvePath(configuredPath);
    }

    private static Path resolvePath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        return Path.of(AuditFallbackProperties.defaultPath());
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
