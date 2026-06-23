package com.medkernel.engine.knowledge;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.VersionedAssetPort;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 知识域接入统一资产版本底座的薄适配器。
 *
 * <p>知识内容继续保存在领域表中，哪个版本生效只由统一版本底座决定。
 */
@Service
public class KnowledgeVersionedAssetAdapter implements VersionedAssetPort {

    private final AssetVersionService delegate;

    public KnowledgeVersionedAssetAdapter(AssetVersionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        if (command.assetType() != null && command.assetType() != VersionedAssetType.KNOWLEDGE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "知识版本适配器只允许登记 KNOWLEDGE 资产");
        }
        return delegate.registerDraft(new AssetVersionRegisterCommand(
            command.tenantId(),
            VersionedAssetType.KNOWLEDGE,
            command.assetIdentity(),
            command.organizationScope(),
            command.applicableScope(),
            command.content(),
            command.contentHash(),
            command.sourceRef(),
            command.createdBy(),
            command.traceId(),
            command.safetyPolicy(),
            command.overridePolicy()
        ));
    }

    @Override
    public AssetVersion updateDraft(AssetVersionDraftUpdateCommand command) {
        return delegate.updateDraft(command);
    }
}
