package com.medkernel.engine.terminology;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 把当前组织范围已确认的院内术语映射固化为不可变术语资产草稿。
 *
 * <p>稳定身份、自动版本和组织范围由统一资产版本底座管理；每条映射快照直接绑定
 * {@code TERMINOLOGY} 版本 ID，不再创建独立容器、条目清单或手工版本号。
 */
@Service
public class TerminologyAssetDraftService {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final TermMappingRepository mappingRepository;
    private final LocalTermRepository localTermRepository;
    private final StandardTermRepository standardTermRepository;
    private final TermMappingSnapshotRepository snapshotRepository;
    private final TerminologyVersionedAssetAdapter versionedAssets;
    private final AuditRecorder auditRecorder;

    public TerminologyAssetDraftService(
            TermMappingRepository mappingRepository,
            LocalTermRepository localTermRepository,
            StandardTermRepository standardTermRepository,
            TermMappingSnapshotRepository snapshotRepository,
            TerminologyVersionedAssetAdapter versionedAssets,
            AuditRecorder auditRecorder) {
        this.mappingRepository = mappingRepository;
        this.localTermRepository = localTermRepository;
        this.standardTermRepository = standardTermRepository;
        this.snapshotRepository = snapshotRepository;
        this.versionedAssets = versionedAssets;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 生成下一版术语资产草稿；统一版本底座自动分配 V1、V2、V3……。
     */
    @Transactional
    public TerminologyAssetDraftResponse createDraft(TerminologyAssetDraftRequest request) {
        OrgScope currentScope = RequestContext.currentOrgScope();
        if (currentScope == null || !currentScope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String tenantId = currentScope.tenantId();
        AssetScope scope = requireCurrentScope(
            tenantId, currentScope, request.scopeLevel(), request.scopeCode());
        List<TermMappingSnapshot> snapshots = mappingRepository
            .findConfirmedByTenantIdAndScope(tenantId, scope.level(), scope.code())
            .stream()
            .map(mapping -> snapshot(tenantId, mapping))
            .toList();
        if (snapshots.isEmpty()) {
            throw ApiException.conflict("当前范围没有已确认映射，无法生成术语资产草稿");
        }

        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        String assetIdentity = request.assetIdentity().trim();
        String content = writeContent(new TerminologyAssetContent(
            assetIdentity,
            request.name().trim(),
            scope.level(),
            scope.code(),
            snapshots
        ));
        AssetVersion version = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            null,
            "ALL",
            content,
            null,
            "terminology:" + assetIdentity,
            actor,
            traceId
        ));

        Instant now = Instant.now();
        for (TermMappingSnapshot snapshot : snapshots) {
            snapshotRepository.save(TermMappingSnapshotEntity.fromSnapshot(
                tenantId,
                version.versionId(),
                snapshot.mappingId(),
                snapshot,
                TermMappingSnapshotCodec.write(snapshot),
                now,
                actor
            ));
        }
        auditRecorder.record(
            AuditAction.CREATE,
            "terminology_asset_version",
            version.versionId(),
            "生成术语资产草稿: " + assetIdentity + "@" + version.versionNo()
                + "，映射 " + snapshots.size() + " 条"
        );
        return TerminologyAssetDraftResponse.from(version, snapshots.size());
    }

    private TermMappingSnapshot snapshot(String tenantId, TermMapping mapping) {
        LocalTerm localTerm = localTermRepository.findByTenantIdAndId(
                tenantId, mapping.localTermId())
            .orElseThrow(() -> ApiException.notFound("院内术语 id=" + mapping.localTermId()));
        StandardTerm standardTerm = standardTermRepository
            .findFirstByTenantIdsAndId(
                standardSources(tenantId), tenantId, mapping.standardTermId())
            .filter(term -> term.status() == StandardTermStatus.ACTIVE)
            .orElseThrow(() -> ApiException.notFound("标准术语 id=" + mapping.standardTermId()));
        return TermMappingSnapshot.from(mapping, localTerm, standardTerm);
    }

    private static List<String> standardSources(String tenantId) {
        if (PlatformAuthority.PLATFORM_TENANT_ID.equals(tenantId)) {
            return List.of(tenantId);
        }
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID, tenantId);
    }

    private static AssetScope requireCurrentScope(
            String tenantId,
            OrgScope current,
            String rawLevel,
            String rawCode) {
        String level = rawLevel == null ? "" : rawLevel.trim().toUpperCase();
        String code = rawCode == null ? "" : rawCode.trim();
        String expectedCode = switch (level) {
            case "TENANT" -> tenantId;
            case "REGION" -> current.groupId();
            case "FACILITY" -> firstNonBlank(current.siteId(), current.hospitalId());
            case "CAMPUS" -> current.campusId();
            case "DEPARTMENT" -> current.departmentId();
            default -> throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "术语资产范围层级不受支持: " + rawLevel
            );
        };
        if (expectedCode == null || expectedCode.isBlank() || !expectedCode.equals(code)) {
            throw new ApiException(
                ErrorCode.ORG_SCOPE_DENIED,
                "术语资产范围必须与当前组织上下文一致"
            );
        }
        return new AssetScope(
            level,
            code
        );
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String writeContent(TerminologyAssetContent content) {
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "术语资产正文序列化失败",
                exception
            );
        }
    }

    private record AssetScope(String level, String code) {
    }

    private record TerminologyAssetContent(
        String assetIdentity,
        String name,
        String scopeLevel,
        String scopeCode,
        List<TermMappingSnapshot> mappings
    ) {
    }
}
