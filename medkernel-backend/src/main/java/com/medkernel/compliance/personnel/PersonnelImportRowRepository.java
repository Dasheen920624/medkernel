package com.medkernel.compliance.personnel;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 人员导入逐行结果仓库。 */
@Repository
public interface PersonnelImportRowRepository
        extends ListCrudRepository<PersonnelImportRow, String> {

    List<PersonnelImportRow> findByTenantIdAndJobIdOrderByRowNoAsc(
        String tenantId,
        String jobId
    );
}
