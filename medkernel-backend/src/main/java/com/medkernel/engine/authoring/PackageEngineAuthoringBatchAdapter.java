package com.medkernel.engine.authoring;

import com.medkernel.engine.pkg.PackageEngineService;
import com.medkernel.engine.pkg.PackageOfflineImportRequest;
import com.medkernel.engine.pkg.PackageOfflineImportResponse;
import com.medkernel.engine.pkg.PackageSyncRequest;
import com.medkernel.engine.pkg.PackageSyncResponse;
import org.springframework.stereotype.Component;

/**
 * 复用配置包离线交换与真实同步端口的批量适配器。
 */
@Component
public class PackageEngineAuthoringBatchAdapter implements AuthoringBatchPackagePort {

    private final PackageEngineService packages;

    public PackageEngineAuthoringBatchAdapter(PackageEngineService packages) {
        this.packages = packages;
    }

    @Override
    public PackageOfflineImportResponse importOfflinePackage(String offlinePackageJson) {
        return packages.importOfflinePackage(new PackageOfflineImportRequest(offlinePackageJson));
    }

    @Override
    public String exportOfflinePackage(String packageId, String targetOrgUnitId) {
        return packages.exportOfflinePackage(packageId, targetOrgUnitId);
    }

    @Override
    public PackageSyncResponse distribute(AuthoringBatchPackageDistributeCommand command) {
        return packages.releasePackage(
            command.packageId(),
            new PackageSyncRequest(
                command.targetOrgUnitId(),
                command.strategy(),
                command.scopeType(),
                command.scopeValue(),
                command.adapterIds(),
                command.reason()));
    }
}
