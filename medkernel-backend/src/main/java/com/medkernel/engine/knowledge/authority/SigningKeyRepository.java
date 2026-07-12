package com.medkernel.engine.knowledge.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 发布签名公钥元数据仓储端口。 */
@org.springframework.stereotype.Repository
public interface SigningKeyRepository extends Repository<SigningKey, Long> {

    SigningKey save(SigningKey signingKey);

    Optional<SigningKey> findByTenantIdAndAuthorityIdAndKeyId(
        String tenantId,
        String authorityId,
        String keyId
    );

    List<SigningKey> findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
        String tenantId,
        String authorityId,
        String issuerInstanceId
    );
}
