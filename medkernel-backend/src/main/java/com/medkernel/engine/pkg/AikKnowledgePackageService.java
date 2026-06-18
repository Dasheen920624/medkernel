package com.medkernel.engine.pkg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;

/**
 * 将 AI 工厂已审知识资产装配为 PKG-01 知识包草稿。
 */
@Service
public class AikKnowledgePackageService {

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository packageItemRepository;
    private final AikPackJobRepository packJobRepository;
    private final KnowledgeIdentityRepository identityRepository;
    private final KnowledgeAssetVersionRepository versionRepository;
    private final PackageVersionedAssetAdapter versionedAssets;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper json;

    public AikKnowledgePackageService(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository packageItemRepository,
            AikPackJobRepository packJobRepository,
            KnowledgeIdentityRepository identityRepository,
            KnowledgeAssetVersionRepository versionRepository,
            PackageVersionedAssetAdapter versionedAssets,
            AuditRecorder auditRecorder,
            ObjectMapper json) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.packJobRepository = packJobRepository;
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.versionedAssets = versionedAssets;
        this.auditRecorder = auditRecorder;
        this.json = json;
    }

    /**
     * 只允许当前 ACTIVE 的知识资产版本进入知识包，不把待审候选或历史版本打包。
     */
    @Transactional
    public AikPackageBuildResponse build(AikPackageBuildRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        String packageCode = requireText(request.packageCode(), "包编码不能为空");
        String packageVersion = requireText(request.packageVersion(), "包版本不能为空");
        String name = requireText(request.name(), "包名称不能为空");
        List<Long> versionIds = requireVersionIds(request.assetVersionIds());

        packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(tenantId, packageCode, packageVersion)
            .ifPresent(existing -> {
                throw new ApiException(ErrorCode.ENG_PACKAGE_004, "知识包版本在该租户内已存在: " + packageVersion);
            });

        List<AikManifestItem> manifestItems = versionIds.stream()
            .map(versionId -> manifestItem(tenantId, versionId))
            .sorted(Comparator.comparing(AikManifestItem::assetVersionId))
            .toList();
        Instant now = Instant.now();
        KnowledgePackage pack = packageRepository.save(new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageCode,
            packageVersion,
            name,
            request.description(),
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.DRAFT,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        for (AikManifestItem item : manifestItems) {
            packageItemRepository.save(new PackageItem(
                null,
                UUID.randomUUID().toString(),
                tenantId,
                pack.packageId(),
                VersionedAssetType.KNOWLEDGE,
                item.identityCode(),
                item.versionNo(),
                now,
                actor,
                now,
                actor,
                traceId
            ));
        }

        AikPackageManifest manifest = new AikPackageManifest(
            packageCode, packageVersion, manifestItems.size(), manifestItems);
        String manifestJson = writeJson(manifest);
        String manifestSha256 = sha256(manifestJson);
        String jobId = UUID.randomUUID().toString();
        packJobRepository.save(new AikPackJob(
            null,
            jobId,
            tenantId,
            pack.packageId(),
            packageCode,
            packageVersion,
            manifestItems.size(),
            manifestJson,
            manifestSha256,
            AikPackJobStatus.PACKAGED,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.PACKAGE,
            packageCode,
            packageVersion,
            "tenant:" + tenantId,
            "ALL",
            manifestJson,
            manifestSha256,
            "aik-pack-job:" + jobId,
            actor,
            traceId
        ));
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_aik_pack_job",
            jobId,
            "装配 AIK 知识包草稿: " + name + " (" + packageVersion + ")，资产数 " + manifestItems.size()
        );
        return new AikPackageBuildResponse(jobId, PackageResponse.from(pack), manifestItems.size(), manifestSha256);
    }

    private AikManifestItem manifestItem(String tenantId, Long versionId) {
        KnowledgeAssetVersion version = versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_DEPENDENCY_MISSING, "已审知识版本不存在: " + versionId));
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, version.identityId())
            .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "知识身份不存在: " + version.identityId()));
        if (!identity.isActive()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 ACTIVE 状态的知识身份入包, 当前: " + identity.status());
        }
        if (!version.isAuthoritative()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 ACTIVE 状态的知识版本入包, 当前: " + version.status());
        }
        return new AikManifestItem(
            version.id(),
            identity.id(),
            identity.identityCode(),
            version.versionNo(),
            version.contentHash(),
            version.effectiveOrganizationScope(),
            version.effectiveApplicableScope()
        );
    }

    private List<Long> requireVersionIds(List<Long> assetVersionIds) {
        if (assetVersionIds == null || assetVersionIds.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "已审知识资产版本列表不能为空");
        }
        Set<Long> seen = new HashSet<>();
        for (Long versionId : assetVersionIds) {
            if (versionId == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "已审知识资产版本 ID 不能为空");
            }
            if (!seen.add(versionId)) {
                throw new ApiException(ErrorCode.CONFLICT, "同一知识资产版本不能重复入包: " + versionId);
            }
        }
        return List.copyOf(assetVersionIds);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AIK 知识包清单无法序列化", exception);
        }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "知识包摘要算法不可用", exception);
        }
    }

    private record AikPackageManifest(
        String packageCode,
        String packageVersion,
        int itemCount,
        List<AikManifestItem> items
    ) {}

    private record AikManifestItem(
        Long assetVersionId,
        Long identityId,
        String identityCode,
        String versionNo,
        String contentHash,
        String organizationScope,
        String applicableScope
    ) {}
}
