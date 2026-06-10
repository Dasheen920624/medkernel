package com.medkernel.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 配置中心读时播种事务语义测试。
 *
 * <p>回归 2026-06-10 真实 PostgreSQL 首次部署缺陷：{@code getOrSeedTenantConfig} 被
 * {@code @Transactional(readOnly = true)} 的读方法调用（如通知设置读取）时，其播种 INSERT
 * 以 REQUIRED 传播加入外层只读事务，真实 PostgreSQL 抛 SQLSTATE 25006
 * "cannot execute INSERT in a read-only transaction"，空库首次打开页面即失败。
 * H2 不强制只读，无法直接复现报错，故本测试改为锁定修复后的提交语义：
 * 播种必须在独立读写事务中完成——外层只读事务尚未结束时，种子行已对其他连接可见。
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemConfigSeedTransactionTest {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String key;

    @AfterEach
    void tearDown() {
        if (key != null) {
            jdbcTemplate.update("DELETE FROM mk_config_item WHERE config_key = ?", key);
            jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key = ?", key);
        }
    }

    @Test
    void seedFromReadOnlyCallerCommitsInIndependentWriteTransaction() {
        key = "medkernel.test.seed-tx." + UUID.randomUUID();
        SystemConfigSeed seed = new SystemConfigSeed(
            "t-1",
            key,
            "{}",
            "JSON",
            "事务语义测试种子",
            "LOW",
            "测试",
            "验证读时播种走独立读写事务",
            "DB",
            false,
            Instant.now());

        TransactionTemplate readOnlyCaller = new TransactionTemplate(transactionManager);
        readOnlyCaller.setReadOnly(true);

        readOnlyCaller.executeWithoutResult(status -> {
            systemConfigService.getOrSeedTenantConfig("t-1", key, seed, "tester");

            // 挂起外层事务、用独立连接读取：种子行必须已提交可见，
            // 证明播种没有加入外层只读事务（真实 PG 上加入即 25006 失败）。
            TransactionTemplate independentReader = new TransactionTemplate(transactionManager);
            independentReader.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            Integer committed = independentReader.execute(s -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mk_config_item WHERE tenant_id = 't-1' AND config_key = ?",
                Integer.class, key));

            assertThat(committed)
                .as("外层只读事务未结束时，种子行应已由独立读写事务提交")
                .isEqualTo(1);
        });
    }
}
