package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.pkg.PackageOfflineImportResponse;
import com.medkernel.engine.pkg.PackageSyncResponse;
import com.medkernel.engine.pkg.ReleaseScopeType;
import com.medkernel.engine.pkg.ReleaseStrategy;

/**
 * 批量创作对配置包导入、导出与分发能力的调用端口。
 */
public interface AuthoringBatchPackagePort {

    PackageOfflineImportResponse importOfflinePackage(String offlinePackageJson);

    String exportOfflinePackage(String packageId, String targetOrgUnitId);

    PackageSyncResponse distribute(AuthoringBatchPackageDistributeCommand command);
}

record AuthoringBatchPackageDistributeCommand(
    String packageId,
    String targetOrgUnitId,
    ReleaseStrategy strategy,
    ReleaseScopeType scopeType,
    String scopeValue,
    List<String> adapterIds,
    String reason
) {
    AuthoringBatchPackageDistributeCommand {
        adapterIds = adapterIds == null ? List.of() : List.copyOf(adapterIds);
    }
}
