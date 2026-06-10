package com.medkernel.shared.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置中心读时播种写入器。
 *
 * <p>读路径（如 {@code @Transactional(readOnly = true)} 的设置读取）在配置缺失时按需播种
 * 安全默认值；播种 INSERT 若以 REQUIRED 传播加入外层只读事务，真实 PostgreSQL 会拒绝
 * （SQLSTATE 25006），空库首次访问页面即失败。本组件以 {@code REQUIRES_NEW} 挂起调用方
 * 事务、在独立读写事务中写入并立即提交，使读路径调用方保持只读语义。
 *
 * <p>独立成 bean 而非服务自调用，是因为 Spring 事务代理对自调用不生效。
 */
@Component
public class SystemConfigSeedWriter {

    private final SystemConfigRepository repository;

    public SystemConfigSeedWriter(SystemConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 在独立读写事务中插入种子配置；键已存在时由仓库幂等忽略，不覆盖真实运维值。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertSeedIfAbsent(SystemConfigSeed seed, String actor) {
        repository.insertSeedIfAbsent(seed, actor);
    }
}
