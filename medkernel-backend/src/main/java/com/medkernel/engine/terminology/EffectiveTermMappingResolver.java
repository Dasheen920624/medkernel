package com.medkernel.engine.terminology;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 解析当前组织真正可消费的术语映射。
 *
 * <p>仅查询统一资产版本为 ACTIVE、领域包状态为 PUBLISHED 的不可变包条目；
 * 同一编码锚点同时命中多层组织包时，仅保留最具体层级，避免上级基线与下级覆盖形成伪冲突。
 */
@Service
public class EffectiveTermMappingResolver {

    private static final Map<String, Integer> SCOPE_RANK = Map.of(
        "TENANT", 1,
        "GROUP", 2,
        "HOSPITAL", 3,
        "CAMPUS", 4,
        "SITE", 5,
        "DEPARTMENT", 6
    );

    private final TermMappingPackageItemRepository packageItems;

    public EffectiveTermMappingResolver(TermMappingPackageItemRepository packageItems) {
        this.packageItems = packageItems;
    }

    public List<EffectiveTermMapping> resolve(
            String tenantId,
            String sourceSystem,
            String localCode,
            String targetDictionaryKey,
            String category) {
        OrgScope scope = effectiveScope(tenantId);
        List<EffectiveTermMappingCandidate> candidates = packageItems.findEffectiveByAnchor(
            tenantId,
            scope.tenantId(),
            scope.groupId(),
            scope.hospitalId(),
            scope.campusId(),
            scope.siteId(),
            scope.departmentId(),
            sourceSystem,
            localCode,
            targetDictionaryKey,
            category
        );
        return resolveMostSpecific(candidates);
    }

    public int countByStandardCode(String tenantId, String targetDictionaryKey, String standardCode) {
        OrgScope scope = effectiveScope(tenantId);
        return resolveMostSpecific(packageItems.findEffectiveByStandardCode(
            tenantId,
            scope.tenantId(),
            scope.groupId(),
            scope.hospitalId(),
            scope.campusId(),
            scope.siteId(),
            scope.departmentId(),
            targetDictionaryKey,
            standardCode
        )).size();
    }

    private List<EffectiveTermMapping> resolveMostSpecific(
            List<EffectiveTermMappingCandidate> candidates) {
        int mostSpecificRank = candidates.stream()
            .map(EffectiveTermMappingCandidate::scopeLevel)
            .mapToInt(EffectiveTermMappingResolver::scopeRank)
            .max()
            .orElse(0);
        Map<String, EffectiveTermMapping> distinctTargets = new LinkedHashMap<>();
        candidates.stream()
            .filter(candidate -> scopeRank(candidate.scopeLevel()) == mostSpecificRank)
            .sorted(Comparator.comparing(
                EffectiveTermMappingCandidate::mappingId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .forEach(candidate -> distinctTargets.putIfAbsent(
                targetKey(candidate),
                new EffectiveTermMapping(
                    candidate.mappingId(),
                    candidate.standardTermId(),
                    candidate.standardCode()
                )
            ));
        return List.copyOf(distinctTargets.values());
    }

    private static OrgScope effectiveScope(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        OrgScope current = RequestContext.currentOrgScope();
        if (current == null || !current.hasTenant()) {
            return OrgScope.tenant(tenantId.trim());
        }
        if (!tenantId.trim().equals(current.tenantId())) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "术语映射租户与当前组织上下文不一致");
        }
        return current;
    }

    private static int scopeRank(String scopeLevel) {
        if (scopeLevel == null) {
            return 0;
        }
        return SCOPE_RANK.getOrDefault(scopeLevel.trim().toUpperCase(), 0);
    }

    private static String targetKey(EffectiveTermMappingCandidate candidate) {
        if (candidate.standardTermId() != null) {
            return "id:" + candidate.standardTermId();
        }
        return "code:" + candidate.standardCode();
    }
}
