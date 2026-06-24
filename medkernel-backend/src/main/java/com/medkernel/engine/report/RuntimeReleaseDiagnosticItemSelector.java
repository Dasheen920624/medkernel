package com.medkernel.engine.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从机构生效版本中选择医技报告解读可用的医技项目说明书版本。
 *
 * <p>医技报告解读是运行结果，不是知识内容域；这里仅锁定其可引用的说明书知识版本。
 */
@Component
public class RuntimeReleaseDiagnosticItemSelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;

    public RuntimeReleaseDiagnosticItemSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions) {
        this.runtime = runtime;
        this.identities = identities;
        this.versions = versions;
    }

    public List<RuntimeDiagnosticItemReference> select(String tenantId, String runtimeReleaseId) {
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        List<RuntimeDiagnosticItemReference> selected = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.KNOWLEDGE) {
                continue;
            }
            KnowledgeIdentity identity = identities
                .findByTenantIdAndIdentityCode(item.sourceTenantId(), item.assetIdentity())
                .orElseThrow(() -> invalid("机构生效版本锁定知识身份不存在：" + item.assetIdentity()));
            if (identity.domain() != KnowledgeDomain.DIAGNOSTIC_ITEM) {
                continue;
            }
            KnowledgeAssetVersion version = versions
                .findByTenantIdAndIdentityIdAndVersionNo(
                    item.sourceTenantId(),
                    identity.id(),
                    requireText(item.versionNo(), "医技项目说明书版本"))
                .orElseThrow(() -> invalid(
                    "机构生效版本锁定医技项目说明书版本不存在："
                        + item.assetIdentity() + "@" + item.versionNo()));
            if (version.status() != KnowledgeVersionStatus.ACTIVE) {
                throw invalid(
                    "机构生效版本锁定医技项目说明书版本未激活："
                        + item.assetIdentity() + "@" + item.versionNo());
            }
            selected.add(new RuntimeDiagnosticItemReference(
                item.sourceTenantId(),
                identity.id(),
                identity.identityCode(),
                identity.subject(),
                version.id(),
                version.versionNo(),
                version.authorityLevel() == null ? null : version.authorityLevel().name(),
                version.contentHash()
            ));
        }
        return List.copyOf(selected);
    }

    private ClinicalRuntimeReleaseContent resolve(String tenantId, String runtimeReleaseId) {
        try {
            return runtime.resolve(
                requireText(tenantId, "tenantId"),
                requireText(runtimeReleaseId, "runtimeReleaseId"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_ASSET_002, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_ASSET_002, message, cause);
    }
}
