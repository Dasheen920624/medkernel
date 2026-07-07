package com.medkernel.engine.terminology;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 解析指定机构生效版本真正可消费的术语映射。
 *
 * <p>只读取运行清单锁定的 PUBLISHED 术语资产版本；同一编码锚点命中多层组织版本时，
 * 仅保留最具体层级，避免平台标准版本与医院覆盖形成伪冲突。
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
    private final OrgHierarchyRepository orgHierarchy;
    private final OrgUnitRepository orgUnits;
    private final ClinicalRuntimeReleaseRepository runtimeReleases;

    public EffectiveTermMappingResolver(
            TermMappingSnapshotRepository snapshots,
            OrgHierarchyRepository orgHierarchy,
            OrgUnitRepository orgUnits,
            ClinicalRuntimeReleaseRepository runtimeReleases) {
        this.snapshots = snapshots;
        this.orgHierarchy = orgHierarchy;
        this.orgUnits = orgUnits;
        this.runtimeReleases = runtimeReleases;
    }

    public List<EffectiveTermMapping> resolve(
            String tenantId,
            String runtimeReleaseId,
            String sourceSystem,
            String localCode,
            String targetDictionaryKey,
            String category) {
        OrgScope scope = effectiveScope(tenantId);
        EffectiveOrgPaths orgPaths = effectiveOrgPaths(tenantId, scope);
        String releaseId = required(runtimeReleaseId, "术语映射解析必须指定机构生效版本");
        requireRuntimeReleaseBelongsToCurrentHospital(tenantId, releaseId, scope);
        List<EffectiveTermMappingCandidate> candidates = snapshots.findEffectiveByAnchor(
            tenantId,
            releaseId,
            orgPaths.organizationScopes(),
            orgPaths.regionOrgPath(),
            orgPaths.facilityOrgPath(),
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
        EffectiveOrgPaths orgPaths = effectiveOrgPaths(tenantId, scope);
        String releaseId = required(runtimeReleaseId, "术语覆盖率评估必须指定机构生效版本");
        requireRuntimeReleaseBelongsToCurrentHospital(tenantId, releaseId, scope);
        return resolveMostSpecific(snapshots.findEffectiveByStandardCode(
            tenantId,
            releaseId,
            orgPaths.organizationScopes(),
            orgPaths.regionOrgPath(),
            orgPaths.facilityOrgPath(),
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

    private EffectiveOrgPaths effectiveOrgPaths(String tenantId, OrgScope scope) {
        String nearestOrgUnitId = scope.nearestOrgUnitId();
        if (nearestOrgUnitId == null || nearestOrgUnitId.isBlank()) {
            return new EffectiveOrgPaths(List.of(tenantRootOrgPath(tenantId)), null, null);
        }
        List<OrgUnit> ancestors = orgHierarchy.findResolutionAncestorsAndSelf(tenantId, nearestOrgUnitId);
        if (ancestors.isEmpty()) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "术语映射组织上下文不在当前租户组织树中");
        }
        List<OrgUnit> ownerScopes = ancestors.stream()
            .filter(OrgUnit::isActive)
            .filter(EffectiveTermMappingResolver::isVersionOwnerScope)
            .distinct()
            .toList();
        if (ownerScopes.isEmpty()) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "术语映射组织上下文缺少可用版本归属范围");
        }
        return new EffectiveOrgPaths(
            ownerScopes.stream().map(OrgUnit::orgPath).toList(),
            lastOrgPath(ownerScopes, OrgLevel.REGION),
            lastOrgPath(ownerScopes, OrgLevel.FACILITY)
        );
    }

    private String tenantRootOrgPath(String tenantId) {
        return orgUnits.findByTenantIdAndParentIdIsNull(tenantId)
            .filter(OrgUnit::isActive)
            .map(OrgUnit::orgPath)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "租户根组织不存在，无法解析术语映射范围"));
    }

    private static boolean isVersionOwnerScope(OrgUnit unit) {
        return unit.level() == OrgLevel.TENANT
            || unit.level() == OrgLevel.REGION
            || unit.level() == OrgLevel.FACILITY;
    }

    private static String lastOrgPath(List<OrgUnit> orgUnits, OrgLevel level) {
        return orgUnits.stream()
            .filter(unit -> unit.level() == level)
            .reduce((left, right) -> right)
            .map(OrgUnit::orgPath)
            .orElse(null);
    }

    private void requireRuntimeReleaseBelongsToCurrentHospital(
            String tenantId,
            String runtimeReleaseId,
            OrgScope scope) {
        String hospitalId = required(scope.hospitalId(), "术语映射解析必须携带当前医院上下文");
        ClinicalRuntimeRelease release = runtimeReleases
            .findByTenantIdAndReleaseId(tenantId.trim(), runtimeReleaseId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_CONTEXT_002,
                "机构生效版本不存在或不属于当前租户"
            ));
        if (!hospitalId.equals(release.hospitalId())) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "机构生效版本不属于当前医院");
        }
    }

    private record EffectiveOrgPaths(
        List<String> organizationScopes,
        String regionOrgPath,
        String facilityOrgPath
    ) {
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
