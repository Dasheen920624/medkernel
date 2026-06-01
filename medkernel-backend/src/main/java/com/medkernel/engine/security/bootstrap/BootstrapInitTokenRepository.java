package com.medkernel.engine.security.bootstrap;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 首发部署 init token 仓库：按 hash 查最近一次登记记录。
 */
@Repository
public interface BootstrapInitTokenRepository extends ListCrudRepository<BootstrapInitToken, Long> {

    Optional<BootstrapInitToken> findFirstByTokenHashOrderByCreatedAtDesc(String tokenHash);
}
