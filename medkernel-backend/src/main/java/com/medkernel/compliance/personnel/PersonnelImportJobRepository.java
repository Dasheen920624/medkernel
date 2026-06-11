package com.medkernel.compliance.personnel;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 人员导入任务仓库。 */
@Repository
public interface PersonnelImportJobRepository
        extends ListCrudRepository<PersonnelImportJob, String> {

    Optional<PersonnelImportJob> findByTenantIdAndJobId(String tenantId, String jobId);

    Optional<PersonnelImportJob> findByTenantIdAndFileDigest(String tenantId, String fileDigest);
}
