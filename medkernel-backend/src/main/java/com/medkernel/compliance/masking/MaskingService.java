package com.medkernel.compliance.masking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.security.DataAccessLevel;
import com.medkernel.shared.security.ResolvedDataScope;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;

/**
 * SYS-06 后端脱敏规则服务。
 */
@Service
public class MaskingService {

    static final String DEFAULT_SCENARIO = "DEFAULT";
    private static final String TARGET_TYPE = "mk_compliance_masking_rule";
    private static final int AUDIT_SUMMARY_MAX_LENGTH = 512;

    private final MaskingRuleRepository repository;
    private final AuditRecorder auditRecorder;

    public MaskingService(MaskingRuleRepository repository, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    public List<MaskingRuleResponse> listRules(String tenantId, String resourceType, String fieldName) {
        String safeTenant = requireTenant(tenantId);
        String normalizedResource = resourceType == null || resourceType.isBlank()
            ? null : normalizeResourceType(resourceType);
        String normalizedField = fieldName == null || fieldName.isBlank()
            ? null : normalizeFieldName(fieldName);
        return repository.findRules(safeTenant, normalizedResource, normalizedField).stream()
            .map(MaskingRuleResponse::from)
            .toList();
    }

    @Transactional
    public MaskingRuleResponse upsertRule(String tenantId, MaskingRuleRequest request, String actor) {
        String safeTenant = requireTenant(tenantId);
        String resourceType = normalizeResourceType(request.resourceType());
        String fieldName = normalizeFieldName(request.fieldName());
        String scenarioCode = normalizeScenario(request.scenarioCode());
        String maskChar = normalizeMaskChar(request.maskChar());
        Integer prefixKeep = normalizeKeep(request.prefixKeep());
        Integer suffixKeep = normalizeKeep(request.suffixKeep());
        Instant now = Instant.now();
        String safeActor = safeActor(actor);

        var existing = repository.findByTenantIdAndResourceTypeAndFieldNameAndScenarioCode(
            safeTenant, resourceType, fieldName, scenarioCode);
        if (existing.isPresent() && request.expectedVersion() != null
                && !request.expectedVersion().equals(existing.get().version())) {
            throw ApiException.conflict("脱敏规则版本冲突");
        }
        if (existing.isEmpty() && request.expectedVersion() != null) {
            throw ApiException.conflict("新建脱敏规则不能携带 expectedVersion");
        }

        MaskingRule before = existing.orElse(null);
        MaskingRule saved = repository.save(new MaskingRule(
            before == null ? null : before.id(),
            before == null ? ruleId(resourceType, fieldName, scenarioCode) : before.ruleId(),
            safeTenant,
            resourceType,
            fieldName,
            scenarioCode,
            request.strategy().name(),
            maskChar,
            prefixKeep,
            suffixKeep,
            request.status().name(),
            before == null ? 1L : before.version() + 1L,
            before == null ? now : before.createdAt(),
            before == null ? safeActor : before.createdBy(),
            now,
            safeActor,
            RequestContext.currentTraceId()));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            TARGET_TYPE,
            saved.ruleId(),
            auditSummary(saved, request.reason()),
            before,
            saved,
            null));
        return MaskingRuleResponse.from(saved);
    }

    public MaskingResult mask(ResolvedDataScope resolved, MaskingRequest request) {
        String safeTenant = requireTenant(request.tenantId());
        String resourceType = normalizeResourceType(request.resourceType());
        String scenarioCode = normalizeScenario(request.scenarioCode());
        Map<String, Object> values = new LinkedHashMap<>(request.values() == null ? Map.of() : request.values());
        List<String> sensitiveFields = normalizeSensitiveFields(request.sensitiveFields());
        List<MaskingRule> rules = sensitiveFields.stream()
            .map(field -> findRuleOrFail(safeTenant, resourceType, field, scenarioCode))
            .toList();
        boolean rawAllowed = rawAllowed(resolved, safeTenant);
        if (rawAllowed) {
            return new MaskingResult(resourceType, scenarioCode, values, List.of(), true);
        }

        List<String> maskedFields = new ArrayList<>();
        for (MaskingRule rule : rules) {
            if (values.containsKey(rule.fieldName())) {
                values.put(rule.fieldName(), maskValue(values.get(rule.fieldName()), rule));
                maskedFields.add(rule.fieldName());
            }
        }
        return new MaskingResult(resourceType, scenarioCode, values, maskedFields, false);
    }

    private MaskingRule findRuleOrFail(String tenantId, String resourceType, String fieldName, String scenarioCode) {
        if (!DEFAULT_SCENARIO.equals(scenarioCode)) {
            var scenarioRule = repository.findActiveRule(tenantId, resourceType, fieldName, scenarioCode);
            if (scenarioRule.isPresent()) {
                return scenarioRule.get();
            }
        }
        return repository.findActiveRule(tenantId, resourceType, fieldName, DEFAULT_SCENARIO)
            .orElseThrow(() -> new ApiException(
                ErrorCode.DATA_SCOPE_DENIED, "脱敏规则未配置：" + resourceType + "." + fieldName));
    }

    private Object maskValue(Object rawValue, MaskingRule rule) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue);
        MaskingStrategy strategy = MaskingStrategy.valueOf(rule.strategy());
        return switch (strategy) {
            case REDACT -> repeatMask(rule.maskChar(), Math.max(3, codePointLength(value)));
            case KEEP_LAST -> keepFirstLast(value, rule.maskChar(), 0, rule.suffixKeep());
            case KEEP_FIRST_LAST -> keepFirstLast(value, rule.maskChar(), rule.prefixKeep(), rule.suffixKeep());
            case EMAIL -> maskEmail(value, rule.maskChar());
            case FIXED -> repeatMask(rule.maskChar(), 3);
        };
    }

    private String keepFirstLast(String value, String maskChar, Integer prefixKeep, Integer suffixKeep) {
        int length = codePointLength(value);
        int prefix = Math.min(normalizeKeep(prefixKeep), length);
        int suffix = Math.min(normalizeKeep(suffixKeep), Math.max(0, length - prefix));
        if (length <= prefix + suffix) {
            return value;
        }
        return left(value, prefix) + repeatMask(maskChar, length - prefix - suffix) + right(value, suffix);
    }

    private String maskEmail(String value, String maskChar) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return keepFirstLast(value, maskChar, 0, 4);
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        return left(local, 1) + repeatMask(maskChar, Math.max(3, codePointLength(local) - 1)) + domain;
    }

    private boolean rawAllowed(ResolvedDataScope resolved, String tenantId) {
        return resolved != null
            && resolved.level() != DataAccessLevel.NONE
            && !resolved.desensitized()
            && tenantId.equals(resolved.scope().tenantId());
    }

    private List<String> normalizeSensitiveFields(List<String> sensitiveFields) {
        if (sensitiveFields == null || sensitiveFields.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String field : sensitiveFields) {
            normalized.add(normalizeFieldName(field));
        }
        return List.copyOf(normalized);
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏资源类型不能为空");
        }
        if (normalized.length() > 128) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏资源类型过长");
        }
        return normalized;
    }

    private String normalizeFieldName(String fieldName) {
        String value = fieldName == null ? "" : fieldName.trim();
        if (!value.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法脱敏字段名：" + value);
        }
        return value;
    }

    private String normalizeScenario(String scenarioCode) {
        String normalized = scenarioCode == null || scenarioCode.isBlank()
            ? DEFAULT_SCENARIO
            : scenarioCode.trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return DEFAULT_SCENARIO;
        }
        if (normalized.length() > 64) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏场景编码过长");
        }
        return normalized;
    }

    private String normalizeMaskChar(String maskChar) {
        String value = maskChar == null ? "" : maskChar.trim();
        if (value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏字符不能为空");
        }
        if (value.codePointCount(0, value.length()) != 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏字符必须为单个字符");
        }
        return value;
    }

    private Integer normalizeKeep(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0 || value > 32) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "脱敏保留位数必须在 0 到 32 之间");
        }
        return value;
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private String left(String value, int count) {
        if (count <= 0) {
            return "";
        }
        return value.substring(0, value.offsetByCodePoints(0, Math.min(count, codePointLength(value))));
    }

    private String right(String value, int count) {
        if (count <= 0) {
            return "";
        }
        int length = codePointLength(value);
        return value.substring(value.offsetByCodePoints(0, Math.max(0, length - count)));
    }

    private String repeatMask(String maskChar, int count) {
        return maskChar.repeat(Math.max(0, count));
    }

    private String ruleId(String resourceType, String fieldName, String scenarioCode) {
        return "mask-" + resourceType.replace('_', '-') + "-" + fieldName + "-"
            + scenarioCode.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String auditSummary(MaskingRule rule, String reason) {
        String summary = "更新脱敏规则：" + rule.resourceType() + "/" + rule.fieldName() + "/" + rule.scenarioCode();
        if (reason != null && !reason.isBlank()) {
            summary = summary + "；原因：" + reason.trim().replaceAll("\\s+", " ");
        }
        if (summary.length() <= AUDIT_SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, AUDIT_SUMMARY_MAX_LENGTH - 3) + "...";
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId.trim();
    }

    private String safeActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return RequestContext.currentUserId().orElse("system");
    }
}
