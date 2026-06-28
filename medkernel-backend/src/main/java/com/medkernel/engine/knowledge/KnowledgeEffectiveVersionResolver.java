package com.medkernel.engine.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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

/**
 * 知识域当前有效版本解析器。
 *
 * <p>统一版本底座决定生效版本；领域版本表可按统一版本号直连，也可由
 * {@code knowledge-version:<identityCode>:<domainVersionNo>} 来源回链定位不可变内容。
 */
@Service
public class KnowledgeEffectiveVersionResolver {

    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;
    private final AssetVersionRepository assetVersions;
    private final InheritanceResolver inheritanceResolver;

    public KnowledgeEffectiveVersionResolver(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            AssetVersionRepository assetVersions,
            InheritanceResolver inheritanceResolver) {
        this.identities = identities;
        this.versions = versions;
        this.assetVersions = assetVersions;
        this.inheritanceResolver = inheritanceResolver;
    }

    public Optional<ResolvedKnowledgeVersion> resolve(
            String requestTenantId,
            String identityCode,
            String applicableScope) {
        String normalizedApplicableScope = normalize(applicableScope, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);
        OrgScope scope = RequestContext.currentOrgScope();
        String targetOrgUnitId = scope == null ? null : scope.nearestOrgUnitId();
        if (hasText(targetOrgUnitId)) {
            try {
                ResolvedAssetVersion resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
                    requestTenantId,
                    VersionedAssetType.KNOWLEDGE,
                    identityCode,
                    normalizedApplicableScope,
                    targetOrgUnitId
                ));
                if (resolved.disabled() || resolved.version() == null) {
                    return Optional.empty();
                }
                return map(identityCode, resolved.version())
                    .map(content -> new ResolvedKnowledgeVersion(
                        content.identity(), content.version(), resolved.version(), resolved));
            } catch (ApiException exception) {
                if (exception.errorCode() != ErrorCode.NOT_FOUND) {
                    throw exception;
                }
                return Optional.empty();
            }
        }

        String currentOrgPath = PlatformTenant.isPlatformTenant(requestTenantId)
            ? PlatformAuthority.PLATFORM_ORG_PATH
            : scope == null ? null : AuditEvent.orgPath(scope);
        Optional<AssetVersion> local = selectPublished(
            requestTenantId, identityCode, normalizedApplicableScope, currentOrgPath);
        Optional<AssetVersion> effective = local.isPresent() || PlatformTenant.isPlatformTenant(requestTenantId)
            ? local
            : selectPublished(
                PlatformTenant.ID,
                identityCode,
                normalizedApplicableScope,
                PlatformAuthority.PLATFORM_ORG_PATH);
        return effective.flatMap(assetVersion -> map(identityCode, assetVersion)
            .map(content -> new ResolvedKnowledgeVersion(
                content.identity(), content.version(), assetVersion, null)));
    }

    private Optional<AssetVersion> selectPublished(
            String tenantId,
            String identityCode,
            String applicableScope,
            String organizationScope) {
        List<AssetVersion> published = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                tenantId,
                VersionedAssetType.KNOWLEDGE,
                identityCode,
                AssetVersionStatus.PUBLISHED);
        List<AssetVersion> applicable = published.stream()
            .filter(version -> applicableScope.equals(version.applicableScope()))
            .toList();
        if (!hasText(organizationScope)) {
            return applicable.size() == 1
                ? Optional.of(applicable.getFirst())
                : Optional.empty();
        }
        return applicable.stream()
            .filter(version -> organizationScope.equals(version.organizationScope()))
            .max(Comparator
                .comparing(AssetVersion::updatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AssetVersion::versionNo));
    }

    private Optional<ContentVersion> map(String identityCode, AssetVersion assetVersion) {
        return identities.findByTenantIdAndIdentityCode(assetVersion.tenantId(), identityCode)
            .flatMap(identity -> findContentVersion(identity, assetVersion)
                .map(version -> new ContentVersion(identity, version)));
    }

    private Optional<KnowledgeAssetVersion> findContentVersion(
            KnowledgeIdentity identity,
            AssetVersion assetVersion) {
        Optional<KnowledgeAssetVersion> direct = versions.findByTenantIdAndIdentityIdAndVersionNo(
            assetVersion.tenantId(), identity.id(), assetVersion.versionNo());
        if (direct.isPresent()) {
            return direct;
        }
        return domainVersionNoFromSourceRef(identity.identityCode(), assetVersion.sourceRef())
            .flatMap(versionNo -> versions.findByTenantIdAndIdentityIdAndVersionNo(
                assetVersion.tenantId(), identity.id(), versionNo))
            .filter(version -> assetVersion.contentHash().equals(version.contentHash()));
    }

    private Optional<String> domainVersionNoFromSourceRef(String identityCode, String sourceRef) {
        String prefix = "knowledge-version:" + identityCode + ":";
        if (!hasText(sourceRef) || !sourceRef.startsWith(prefix) || sourceRef.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(sourceRef.substring(prefix.length()));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ContentVersion(
        KnowledgeIdentity identity,
        KnowledgeAssetVersion version
    ) {
    }

    public record ResolvedKnowledgeVersion(
        KnowledgeIdentity identity,
        KnowledgeAssetVersion version,
        AssetVersion assetVersion,
        ResolvedAssetVersion resolution
    ) {
    }
}
