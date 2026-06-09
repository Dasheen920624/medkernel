package com.medkernel.engine.pathway;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageAccessPolicy;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.PackageResponse;
import com.medkernel.engine.pkg.PackageVersionedAssetAdapter;
import com.medkernel.engine.pkg.PathwayPackageBuildRequest;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;

/**
 * 把专病画像定义保存为统一路径知识包。
 */
@Service
public class PathwayKnowledgePackageService {

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository packageItemRepository;
    private final SpecialtyProfileRepository profileRepository;
    private final PackageVersionedAssetAdapter versionedAssets;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper json;

    public PathwayKnowledgePackageService(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository packageItemRepository,
            SpecialtyProfileRepository profileRepository,
            PackageVersionedAssetAdapter versionedAssets,
            AuditRecorder auditRecorder,
            ObjectMapper json) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.profileRepository = profileRepository;
        this.versionedAssets = versionedAssets;
        this.auditRecorder = auditRecorder;
        this.json = json;
    }

    /**
     * 构建包含路径包标记和专病画像的知识包草稿。
     */
    @Transactional
    public PackageResponse build(PathwayPackageBuildRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String packageCode = request.packageCode().trim();
        String packageVersion = request.packageVersion().trim();
        packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                tenantId, packageCode, packageVersion)
            .ifPresent(existing -> {
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_004,
                    "知识包版本在该租户内已存在: " + packageVersion
                );
            });

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        KnowledgePackage pack = packageRepository.save(new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageCode,
            packageVersion,
            request.name().trim(),
            request.description(),
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.DRAFT,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        packageItemRepository.save(new PackageItem(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            pack.packageId(),
            VersionedAssetType.PATHWAY,
            packageCode,
            packageVersion,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        for (SpecialtyProfileRequest profile : request.profiles()) {
            profileRepository.save(new SpecialtyProfile(
                null,
                UUID.randomUUID().toString(),
                tenantId,
                pack.packageId(),
                profile.profileCode().trim(),
                profile.name().trim(),
                writeJson(profile.stratification()),
                writeJson(profile.entryCriteria()),
                writeJson(profile.exitCriteria()),
                writeJson(profile.followupPlan()),
                now,
                actor,
                now,
                actor,
                traceId
            ));
        }

        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.PACKAGE,
            packageCode,
            packageVersion,
            "tenant:" + tenantId,
            "disease:" + request.diseaseCode().trim(),
            writeContent(request),
            null,
            request.sourceRef().trim(),
            actor,
            traceId,
            AssetVersionSafetyPolicy.NORMAL,
            null
        ));
        auditRecorder.record(
            AuditAction.CREATE,
            "knowledge_package",
            pack.packageId(),
            "构建路径知识包草稿: " + pack.name() + " (" + pack.packageVersion() + ")"
        );
        return PackageResponse.from(pack);
    }

    private String writeContent(PathwayPackageBuildRequest request) {
        return writeJson(new PathwayPackageContent(
            request.packageCode().trim(),
            request.diseaseCode().trim(),
            request.name().trim(),
            request.description(),
            request.profiles()
        ));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "路径知识包内容无法序列化", exception);
        }
    }

    private record PathwayPackageContent(
        String packageCode,
        String diseaseCode,
        String name,
        String description,
        List<SpecialtyProfileRequest> profiles
    ) {
        private PathwayPackageContent {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
        }
    }
}
