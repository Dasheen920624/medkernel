package com.medkernel.engine.recommendation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 推荐来源中的知识版本定位器。
 *
 * <p>新推荐需要知道知识版本所属租户；旧格式未带租户时按请求租户解释。
 */
public final class KnowledgeSourceLocator {

    private static final String PREFIX = "knowledge_version:";
    private static final Pattern TENANT_SCOPED_REF =
        Pattern.compile("(?i)^(?:knowledge[-_]?version|version)[:#\\-/]([^:#/\\s]+)[:#\\-/]([0-9]+)$");
    private static final Pattern LOCAL_REF =
        Pattern.compile("(?i)^(?:knowledge[-_]?version|version)[:#\\-/]([0-9]+)$");

    private KnowledgeSourceLocator() {
    }

    public static String citationLocator(String sourceTenantId, Long versionId) {
        if (versionId == null) {
            return null;
        }
        if (!hasText(sourceTenantId)) {
            return PREFIX + versionId;
        }
        return PREFIX + sourceTenantId.trim() + ":" + versionId;
    }

    public static Optional<KnowledgeVersionRef> parse(
            String value,
            String defaultTenantId,
            boolean allowPlainNumeric) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        Matcher tenantMatcher = TENANT_SCOPED_REF.matcher(trimmed);
        if (tenantMatcher.matches()) {
            return parseLong(tenantMatcher.group(2))
                .map(versionId -> new KnowledgeVersionRef(tenantMatcher.group(1), versionId));
        }
        Matcher localMatcher = LOCAL_REF.matcher(trimmed);
        if (localMatcher.matches() && hasText(defaultTenantId)) {
            return parseLong(localMatcher.group(1))
                .map(versionId -> new KnowledgeVersionRef(defaultTenantId, versionId));
        }
        if (allowPlainNumeric && trimmed.chars().allMatch(Character::isDigit) && hasText(defaultTenantId)) {
            return parseLong(trimmed)
                .map(versionId -> new KnowledgeVersionRef(defaultTenantId, versionId));
        }
        return Optional.empty();
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record KnowledgeVersionRef(String tenantId, Long versionId) {
        public KnowledgeVersionRef {
            tenantId = tenantId == null ? null : tenantId.trim();
        }
    }
}
