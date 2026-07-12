package com.medkernel.engine.knowledge.delivery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/** 医院完整医疗资源包不可变预检账本仓储端口。 */
@org.springframework.stereotype.Repository
public interface FullPackagePreflightRepository extends Repository<FullPackagePreflight, Long> {

    FullPackagePreflight save(FullPackagePreflight preflight);

    Optional<FullPackagePreflight> findByTenantIdAndHospitalIdAndPreflightId(
        String tenantId,
        String hospitalId,
        String preflightId
    );

    /** 锁定预检事实，保证同一预检只能生成一个机构激活账本。 */
    @Query("SELECT * FROM mk_knowledge_package_preflight "
        + "WHERE tenant_id = :tenantId AND hospital_id = :hospitalId "
        + "AND preflight_id = :preflightId FOR UPDATE")
    Optional<FullPackagePreflight> findByTenantIdAndHospitalIdAndPreflightIdForUpdate(
        String tenantId,
        String hospitalId,
        String preflightId
    );

    List<FullPackagePreflight>
        findByTenantIdAndHospitalIdAndAuthorityIdAndReleaseSequenceOrderByCreatedAtDesc(
        String tenantId,
        String hospitalId,
        String authorityId,
        long releaseSequence
    );

}
