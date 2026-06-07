package com.medkernel.shared.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 审计统一入口。
 *
 * <p>任何业务成功留痕必须调用本类；{@link AuditEventPublisher} 仅作为事件总线承载发布，
 * 不再由业务层手写完整事件，避免字段缺漏和哈希口径分叉。
 */
@Component
public class AuditRecorder {

    private static final String REDACTED = "***";
    private static final String DIGEST_PREFIX = "sm3:";

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final SmCryptoService crypto;

    public AuditRecorder(ApplicationEventPublisher publisher, ObjectMapper objectMapper, SmCryptoService crypto) {
        this.publisher = publisher;
        this.objectMapper = objectMapper.copy()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.crypto = crypto;
    }

    public AuditEvent record(AuditAction action, String targetType, String targetId, String summary) {
        return record(new AuditRecordCommand(action, targetType, targetId, summary, null, null, null));
    }

    public AuditEvent record(AuditRecordCommand command) {
        String beforeSnapshot = serializeSnapshot(command.before());
        String afterSnapshot = serializeSnapshot(command.after());
        String digest = DIGEST_PREFIX + crypto.sm3Hex(String.join("\n",
            command.action().name(),
            command.targetType(),
            command.targetId(),
            nullSafe(command.summary()),
            nullSafe(command.environmentKey()),
            nullSafe(beforeSnapshot),
            nullSafe(afterSnapshot)));

        AuditEvent event = AuditEvent.of(
            command.action(),
            command.targetType(),
            command.targetId(),
            command.summary(),
            digest,
            command.environmentKey(),
            beforeSnapshot,
            afterSnapshot,
            Instant.now());
        publisher.publishEvent(event);
        return event;
    }

    private String serializeSnapshot(Object raw) {
        if (raw == null) {
            return null;
        }
        Object redacted = redact(raw);
        try {
            return objectMapper.writeValueAsString(redacted);
        } catch (JsonProcessingException ex) {
            return "{\"snapshot_error\":\"UNSERIALIZABLE\",\"snapshot_type\":\""
                + escape(raw.getClass().getName()) + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Object redact(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, isSensitiveKey(key) ? REDACTED : redact(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(redact(item));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            return redact(objectMapper.convertValue(value, List.class));
        }
        if (isSimpleValue(value)) {
            return value;
        }
        return redact(objectMapper.convertValue(value, Map.class));
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("phone")
            || normalized.contains("mobile")
            || normalized.contains("idcard")
            || normalized.contains("id_card")
            || normalized.contains("身份证");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
