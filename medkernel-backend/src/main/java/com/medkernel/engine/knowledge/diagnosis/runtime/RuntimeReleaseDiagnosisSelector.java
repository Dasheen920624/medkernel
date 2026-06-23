package com.medkernel.engine.knowledge.diagnosis.runtime;

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
 * 从医院运行修订中选择本次临床可用的诊断知识版本。
 *
 * <p>诊断知识仍以关系库领域表为权威正文；运行修订只保存资产身份和版本锁定。
 */
@Component
public class RuntimeReleaseDiagnosisSelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;

    public RuntimeReleaseDiagnosisSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions) {
        this.runtime = runtime;
        this.identities = identities;
        this.versions = versions;
    }

    public List<RuntimeDiagnosisReference> select(String tenantId, String runtimeReleaseId) {
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        List<RuntimeDiagnosisReference> selected = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.KNOWLEDGE) {
                continue;
            }
            KnowledgeIdentity identity = identities
                .findByTenantIdAndIdentityCode(item.sourceTenantId(), item.assetIdentity())
                .orElseThrow(() -> invalid("运行修订锁定知识身份不存在：" + item.assetIdentity()));
            if (identity.domain() != KnowledgeDomain.DIAGNOSIS) {
                continue;
            }
            KnowledgeAssetVersion version = versions
                .findByTenantIdAndIdentityIdAndVersionNo(
                    item.sourceTenantId(),
                    identity.id(),
                    requireText(item.versionNo(), "诊断知识版本"))
                .orElseThrow(() -> invalid(
                    "运行修订锁定诊断知识版本不存在："
                        + item.assetIdentity() + "@" + item.versionNo()));
            if (version.status() != KnowledgeVersionStatus.ACTIVE) {
                throw invalid(
                    "运行修订锁定诊断知识版本未激活："
                        + item.assetIdentity() + "@" + item.versionNo());
            }
            selected.add(new RuntimeDiagnosisReference(
                item.sourceTenantId(),
                identity.id(),
                identity.identityCode(),
                identity.subject(),
                version.id(),
                version.versionNo(),
                version.authorityLevel() == null ? null : version.authorityLevel().name()
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
        return new ApiException(ErrorCode.ENG_DX_001, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_DX_001, message, cause);
    }
}
