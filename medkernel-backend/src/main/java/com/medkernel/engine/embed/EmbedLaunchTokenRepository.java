package com.medkernel.engine.embed;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 嵌入启动令牌存储库。
 *
 * <p>提供根据 token 查询和持久化启动令牌的接口方法。
 */
public interface EmbedLaunchTokenRepository extends CrudRepository<EmbedLaunchToken, Long> {

    /**
     * 根据启动令牌查询实体。
     *
     * @param token 安全启动令牌
     * @return 启动令牌实体实例包装
     */
    Optional<EmbedLaunchToken> findByToken(String token);

    /**
     * 仅当令牌仍未使用且未过期时原子消费，防止并发重复兑换。
     *
     * @return 更新行数；1 表示消费成功，0 表示已被使用、过期或不存在
     */
    @Modifying
    @Query("""
        UPDATE embed_launch_token
           SET status = 'USED',
               consumed_at = :consumedAt,
               expired_at = :sessionExpiredAt,
               updated_at = :consumedAt,
               updated_by = :updatedBy
         WHERE token = :token
           AND tenant_id = :tenantId
           AND status = 'UNUSED'
           AND expired_at > :now
        """)
    int consumeUnusedToken(
        String token,
        String tenantId,
        Instant now,
        Instant sessionExpiredAt,
        Instant consumedAt,
        String updatedBy);
}
