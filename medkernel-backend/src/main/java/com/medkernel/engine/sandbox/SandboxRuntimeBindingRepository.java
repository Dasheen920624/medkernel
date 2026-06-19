package com.medkernel.engine.sandbox;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 沙盘运行绑定仓储。 */
@Repository
public interface SandboxRuntimeBindingRepository extends ListCrudRepository<SandboxRuntimeBinding, Long> {

    List<SandboxRuntimeBinding> findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
        String tenantId,
        SandboxRuntimeBindingStatus status
    );
}
