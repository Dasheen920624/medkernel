package com.medkernel.engine.context;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;

/**
 * 基于关系库权威配置包的版本解析器。
 */
@Component
public class KnowledgePackageVersionAdapter implements PackageVersionPort {

    private final KnowledgePackageRepository repository;

    public KnowledgePackageVersionAdapter(KnowledgePackageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String tenantId, String version) {
        if (tenantId == null || tenantId.isBlank() || version == null || version.isBlank()) {
            return false;
        }
        return repository.findByTenantIdAndPackageVersion(tenantId.trim(), version.trim()).stream()
            .filter(pack -> pack.status() == KnowledgePackageStatus.ACTIVE)
            .limit(2)
            .count() == 1;
    }

    @Override
    public Optional<String> getActive(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(
                tenantId.trim(), KnowledgePackageStatus.ACTIVE)
            .map(pack -> pack.packageVersion());
    }
}
