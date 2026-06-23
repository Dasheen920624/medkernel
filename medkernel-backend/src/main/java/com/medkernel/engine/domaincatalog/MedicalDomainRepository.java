package com.medkernel.engine.domaincatalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台统一医疗领域目录仓储。
 */
@Repository
public interface MedicalDomainRepository
        extends ListCrudRepository<MedicalDomainDefinition, Long> {

    Optional<MedicalDomainDefinition> findByDomainCode(String domainCode);

    List<MedicalDomainDefinition> findByStatusOrderBySortOrderAscDomainCodeAsc(
        MedicalDomainStatus status
    );
}
