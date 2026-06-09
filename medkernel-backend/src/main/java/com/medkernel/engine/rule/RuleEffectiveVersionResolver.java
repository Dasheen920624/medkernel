package com.medkernel.engine.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;

/**
 * 规则域当前有效版本解析器。
 *
 * <p>统一资产版本决定运行时生效版本，规则定义中的旧版本指针仅保留为编辑态元数据。
 */
@Service
public class RuleEffectiveVersionResolver {

    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;
    private final AssetVersionRepository assetVersions;
    private final InheritanceResolver inheritanceResolver;

    public RuleEffectiveVersionResolver(
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions,
            AssetVersionRepository assetVersions,
            InheritanceResolver inheritanceResolver) {
        this.definitions = definitions;
        this.versions = versions;
        this.assetVersions = assetVersions;
        this.inheritanceResolver = inheritanceResolver;
    }

    public Optional<ResolvedRuleVersion> resolve(
            String requestTenantId,
            String ruleCode,
            String applicableScope) {
        RuleDefinition candidate = definitions.findByTenantIdAndRuleCode(requestTenantId, ruleCode)
            .or(() -> PlatformTenant.isPlatformTenant(requestTenantId)
                ? Optional.empty()
                : definitions.findByTenantIdAndRuleCode(
                    PlatformAuthority.PLATFORM_TENANT_ID, ruleCode))
            .orElse(null);
        return resolve(requestTenantId, candidate, ruleCode, applicableScope);
    }

    public Optional<ResolvedRuleVersion> resolve(
            String requestTenantId,
            RuleDefinition candidate,
            String applicableScope) {
        if (candidate == null) {
            return Optional.empty();
        }
        return resolve(requestTenantId, candidate, candidate.ruleCode(), applicableScope);
    }

    private Optional<ResolvedRuleVersion> resolve(
            String requestTenantId,
            RuleDefinition candidate,
            String ruleCode,
            String applicableScope) {
        String normalizedScope = normalize(applicableScope, "ALL");
        OrgScope scope = RequestContext.currentOrgScope();
        String targetOrgUnitId = scope == null ? null : scope.nearestOrgUnitId();
        if (hasText(targetOrgUnitId)) {
            try {
                ResolvedAssetVersion resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
                    requestTenantId,
                    VersionedAssetType.RULE,
                    ruleCode,
                    normalizedScope,
                    targetOrgUnitId
                ));
                if (resolved.disabled() || resolved.version() == null) {
                    return Optional.empty();
                }
                return map(candidate, ruleCode, resolved.version())
                    .map(content -> new ResolvedRuleVersion(
                        content.rule(), content.version(), resolved.version(), resolved));
            } catch (ApiException exception) {
                if (exception.errorCode() != ErrorCode.NOT_FOUND) {
                    throw exception;
                }
                return Optional.empty();
            }
        }

        String currentOrgPath = PlatformTenant.isPlatformTenant(requestTenantId)
            ? PlatformAuthority.PLATFORM_ORG_PATH
            : AuditEvent.orgPath(scope);
        Optional<AssetVersion> local =
            selectPublished(requestTenantId, ruleCode, normalizedScope, currentOrgPath);
        Optional<AssetVersion> effective = local.isPresent() || PlatformTenant.isPlatformTenant(requestTenantId)
            ? local
            : selectPublished(
                PlatformAuthority.PLATFORM_TENANT_ID,
                ruleCode,
                normalizedScope,
                PlatformAuthority.PLATFORM_ORG_PATH);
        return effective.flatMap(assetVersion -> map(candidate, ruleCode, assetVersion)
            .map(content -> new ResolvedRuleVersion(
                content.rule(), content.version(), assetVersion, null)));
    }

    private Optional<AssetVersion> selectPublished(
            String tenantId,
            String ruleCode,
            String applicableScope,
            String organizationScope) {
        List<AssetVersion> published =
            assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                tenantId,
                VersionedAssetType.RULE,
                ruleCode,
                AssetVersionStatus.PUBLISHED);
        List<AssetVersion> applicable = published.stream()
            .filter(version -> applicableScope.equals(version.applicableScope()))
            .toList();
        Optional<AssetVersion> exact = applicable.stream()
            .filter(version -> hasText(organizationScope)
                && organizationScope.equals(version.organizationScope()))
            .max(Comparator
                .comparing(AssetVersion::updatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(version -> parseVersionNo(version.versionNo())));
        if (exact.isPresent()) {
            return exact;
        }
        return !hasText(organizationScope) && applicable.size() == 1
            ? Optional.of(applicable.getFirst())
            : Optional.empty();
    }

    private Optional<RuleContent> map(
            RuleDefinition candidate,
            String ruleCode,
            AssetVersion assetVersion) {
        int versionNo = parseVersionNo(assetVersion.versionNo());
        Optional<RuleDefinition> rule = candidate != null
                && assetVersion.tenantId().equals(candidate.tenantId())
                && ruleCode.equals(candidate.ruleCode())
            ? Optional.of(candidate)
            : definitions.findByTenantIdAndRuleCode(assetVersion.tenantId(), ruleCode);
        return rule.flatMap(value -> findContentVersion(value, versionNo)
            .map(version -> new RuleContent(value, version)));
    }

    private Optional<RuleVersion> findContentVersion(RuleDefinition rule, int versionNo) {
        Optional<RuleVersion> pointerMatch = hasText(rule.activeVersionId())
            ? versions.findByVersionIdAndTenantId(rule.activeVersionId(), rule.tenantId())
                .filter(version -> version.versionNo() == versionNo)
            : Optional.empty();
        return pointerMatch.isPresent()
            ? pointerMatch
            : versions.findByRuleIdAndTenantIdAndVersionNo(
                rule.ruleId(), rule.tenantId(), versionNo);
    }

    private static int parseVersionNo(String versionNo) {
        try {
            return Integer.parseInt(versionNo);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "规则统一版本号不是有效整数: " + versionNo);
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuleContent(
        RuleDefinition rule,
        RuleVersion version
    ) {
    }

    public record ResolvedRuleVersion(
        RuleDefinition rule,
        RuleVersion version,
        AssetVersion assetVersion,
        ResolvedAssetVersion resolution
    ) {
    }
}
