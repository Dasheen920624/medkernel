package com.medkernel.engine.llm;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
  * 场景模型路由与脱敏配置策略数据访问存储库。
  */
@Repository
public interface ModelCapabilityPolicyRepository extends CrudRepository<ModelCapabilityPolicy, Long> {

    /**
      * 根据租户ID、能力编码和组织作用域唯一获取对应的路由策略与脱敏配置。
      *
      * @param tenantId 租户ID
      * @param capabilityCode 能力代码
      * @param scopeType 作用域类型
      * @param scopeRef 作用域引用
      * @return 路由脱敏策略
      */
    Optional<ModelCapabilityPolicy> findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
        String tenantId,
        String capabilityCode,
        String scopeType,
        String scopeRef);
}
