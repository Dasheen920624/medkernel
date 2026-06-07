package com.medkernel.engine.evaluation;

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
 * 评估指标接入统一资产版本底座的薄适配器。
 */
@Service
public class EvaluationVersionedAssetAdapter implements VersionedAssetPort {

    private final AssetVersionService delegate;

    public EvaluationVersionedAssetAdapter(AssetVersionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        if (command.assetType() != null && command.assetType() != VersionedAssetType.EVALUATION) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "评估版本适配器只允许登记 EVALUATION 资产");
        }
        return delegate.registerDraft(new AssetVersionRegisterCommand(
            command.tenantId(),
            VersionedAssetType.EVALUATION,
            command.assetIdentity(),
            command.versionNo(),
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

    @Override
    public AssetVersion publish(String tenantId, String versionId, String actor) {
        return delegate.publish(tenantId, versionId, actor);
    }

    @Override
    public AssetVersion activate(String tenantId, String versionId, String actor) {
        return delegate.activate(tenantId, versionId, actor);
    }
}
