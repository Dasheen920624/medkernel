package com.medkernel.engine.embed;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * 嵌入 Origin 来源域名允许清单存储库。
 *
 * <p>管理服务机构级别 Origin 来源域名允许清单的持久化及安全阻断校验。
 */
public interface EmbedOriginWhitelistRepository extends CrudRepository<EmbedOriginWhitelist, Long> {

    /**
     * 根据服务机构 ID 拉取所有的授权来源域名列表。
     *
     * @param tenantId 租户ID
     * @return 服务机构来源域名允许清单
     */
    List<EmbedOriginWhitelist> findByTenantId(String tenantId);

    /**
     * 根据服务机构 ID 与域名 Origin 查询来源域名是否允许。
     *
     * @param tenantId 租户ID
     * @param origin 域名Origin（如 https://his.hospital.com）
     * @return 来源域名允许清单实例包装
     */
    Optional<EmbedOriginWhitelist> findByTenantIdAndOrigin(String tenantId, String origin);
}
