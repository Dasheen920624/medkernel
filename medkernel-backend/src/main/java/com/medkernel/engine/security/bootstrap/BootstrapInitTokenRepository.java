package com.medkernel.engine.security.bootstrap;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 首次部署 init token 仓库：按 hash 查最近一次登记记录。
 */
@Repository
public interface BootstrapInitTokenRepository extends ListCrudRepository<BootstrapInitToken, Long> {

    Optional<BootstrapInitToken> findFirstByTokenHashOrderByCreatedAtDesc(String tokenHash);

    /** 锁定全部待消费 token，作为首次接管唯一的数据库互斥点。 */
    @Query("""
        SELECT * FROM mk_security_bootstrap_init_token
        WHERE status = 'ACTIVE'
        ORDER BY id
        FOR UPDATE
        """)
    List<BootstrapInitToken> lockActiveTokens();
}
