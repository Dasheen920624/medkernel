package com.medkernel.engine.pkg;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * AIK 知识包装配作业 Repository。
 */
@Repository
public interface AikPackJobRepository extends ListCrudRepository<AikPackJob, Long> {

    Optional<AikPackJob> findByTenantIdAndJobId(String tenantId, String jobId);
}
