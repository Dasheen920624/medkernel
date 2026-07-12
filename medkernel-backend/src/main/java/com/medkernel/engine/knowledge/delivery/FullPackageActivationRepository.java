package com.medkernel.engine.knowledge.delivery;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 完整医疗资源包机构激活不可变账本仓储端口。 */
@org.springframework.stereotype.Repository
public interface FullPackageActivationRepository
        extends Repository<FullPackageActivation, Long> {

    FullPackageActivation save(FullPackageActivation activation);

    Optional<FullPackageActivation> findByTenantIdAndHospitalIdAndPreflightId(
        String tenantId,
        String hospitalId,
        String preflightId
    );
}
