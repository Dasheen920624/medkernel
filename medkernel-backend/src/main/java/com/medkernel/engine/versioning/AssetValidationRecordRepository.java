package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 资产安全复核证据仓储。
 */
@Repository
public interface AssetValidationRecordRepository
        extends ListCrudRepository<AssetValidationRecord, Long> {

    Optional<AssetValidationRecord> findByValidationId(String validationId);
}
