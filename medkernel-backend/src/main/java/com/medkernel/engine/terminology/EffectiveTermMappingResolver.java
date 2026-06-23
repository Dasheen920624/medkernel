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
 * 解析指定医院运行修订真正可消费的术语映射。
 *
 * <p>只读取运行清单锁定的 PUBLISHED 术语资产版本；同一编码锚点命中多层组织版本时，
 * 仅保留最具体层级，避免平台基线与医院覆盖形成伪冲突。
 */
@Service
public class EffectiveTermMappingResolver {

    private static final Map<String, Integer> SCOPE_RANK = Map.of(
        "TENANT", 1,
        "REGION", 2,
        "FACILITY", 3,
        "CAMPUS", 4,
        "DEPARTMENT", 5
    );

    private final TermMappingSnapshotRepository snapshots;

    public EffectiveTermMappingResolver(TermMappingSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    public List<EffectiveTermMapping> resolve(
            String tenantId,
            String runtimeReleaseId,
            String sourceSystem,
            String localCode,
            String targetDictionaryKey,
            String category) {
        OrgScope scope = effectiveScope(tenantId);
        String releaseId = required(runtimeReleaseId, "术语映射解析必须指定医院运行修订");
        List<EffectiveTermMappingCandidate> candidates = snapshots.findEffectiveByAnchor(
            tenantId,
            releaseId,
            scope.tenantId(),
            scope.groupId(),
            facilityId(scope),
            scope.campusId(),
            scope.departmentId(),
            sourceSystem,
            localCode,
            targetDictionaryKey,
            category
        );
        return resolveMostSpecific(candidates);
    }

    public int countByStandardCode(
            String tenantId,
            String runtimeReleaseId,
            String targetDictionaryKey,
            String standardCode) {
        OrgScope scope = effectiveScope(tenantId);
        return resolveMostSpecific(snapshots.findEffectiveByStandardCode(
            tenantId,
            required(runtimeReleaseId, "术语覆盖率评估必须指定医院运行修订"),
            scope.tenantId(),
            scope.groupId(),
            facilityId(scope),
            scope.campusId(),
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
                    candidate.standardCode(),
                    candidate.versionNo()
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

    private static String facilityId(OrgScope scope) {
        if (scope.siteId() != null && !scope.siteId().isBlank()) {
            return scope.siteId();
        }
        return scope.hospitalId();
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

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }
}
