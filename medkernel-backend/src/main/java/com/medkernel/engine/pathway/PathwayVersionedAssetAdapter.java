package com.medkernel.engine.pathway;

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
 * 路径域接入统一资产版本底座的薄适配器。
 *
 * <p>路径模板、路径包外观下的可发布路径资产统一登记为 {@link VersionedAssetType#PATHWAY}；
 * 生命周期动作委托 SYS-04，避免路径域与知识包各自维护发布规则。
 */
@Service
public class PathwayVersionedAssetAdapter implements VersionedAssetPort {

    private final AssetVersionService delegate;

    public PathwayVersionedAssetAdapter(AssetVersionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        if (command.assetType() != null && command.assetType() != VersionedAssetType.PATHWAY) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "路径版本适配器只允许登记 PATHWAY 资产");
        }
        return delegate.registerDraft(new AssetVersionRegisterCommand(
            command.tenantId(),
            VersionedAssetType.PATHWAY,
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
