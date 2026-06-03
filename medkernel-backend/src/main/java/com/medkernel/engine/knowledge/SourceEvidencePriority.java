package com.medkernel.engine.knowledge;

import java.time.Instant;

/**
 * OPT-07 来源证据排序公共规则：可信分级优先，其次时效，再看适用域精确度。
 */
final class SourceEvidencePriority {

    private SourceEvidencePriority() {
    }

    static int authorityRank(SourceAuthorityLevel level) {
        return level == null ? Integer.MAX_VALUE : level.rank();
    }

    static Instant evidenceTime(SourceVersion sourceVersion) {
        if (sourceVersion != null && sourceVersion.publishedAt() != null) {
            return sourceVersion.publishedAt();
        }
        return null;
    }

    static int compareRecency(Instant target, Instant old) {
        if (target == null && old == null) {
            return 0;
        }
        if (target == null) {
            return -1;
        }
        if (old == null) {
            return 1;
        }
        return target.compareTo(old);
    }

    static int scopeSpecificity(KnowledgeAssetVersion version) {
        if (version == null) {
            return 0;
        }
        return scopeSpecificity(version.effectiveOrganizationScope(), version.effectiveApplicableScope());
    }

    static int scopeSpecificity(String organizationScope, String applicableScope) {
        return scopePartScore(organizationScope) + scopePartScore(applicableScope);
    }

    static String evidenceDate(Instant instant) {
        return instant == null ? "未知" : instant.toString().substring(0, 10);
    }

    private static int scopePartScore(String scope) {
        if (scope == null || scope.isBlank() || KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE.equals(scope)) {
            return 0;
        }
        String normalized = scope.trim().toLowerCase();
        if (normalized.startsWith("specialty:")) {
            return 70;
        }
        if (normalized.startsWith("department:")) {
            return 60;
        }
        if (normalized.startsWith("site:")) {
            return 50;
        }
        if (normalized.startsWith("campus:")) {
            return 40;
        }
        if (normalized.startsWith("hospital:")) {
            return 30;
        }
        if (normalized.startsWith("group:")) {
            return 20;
        }
        if (normalized.startsWith("tenant:")) {
            return 10;
        }
        return 5 + (int) normalized.chars().filter(ch -> ch == '/' || ch == '|').count();
    }
}
