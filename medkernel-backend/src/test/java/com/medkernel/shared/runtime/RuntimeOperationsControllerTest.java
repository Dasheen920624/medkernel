package com.medkernel.shared.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证系统运维快照 Controller 的安全切面防护与国产化数据契约（BASE-07）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeOperationsControllerTest {

    @Autowired
    MockMvc mvc;

    /**
     * 验证在没有授予 system.read 权限时，接口是否被物理安全拦截为 403 Forbidden。
     */
    @Test
    void operationsSnapshotWithoutAuthIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/system/operations"))
            .andExpect(status().isForbidden());
    }

    /**
     * 验证拥有 ROLE_IT_OPS 角色的受托用户，能够顺利穿透安全切面并获取不泄露密钥的运维快照。
     */
    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void operationsSnapshotExposesRuntimeContractWithoutSecrets() throws Exception {
        mvc.perform(get("/api/v1/system/operations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.serviceName").value("medkernel"))
            .andExpect(jsonPath("$.data.deploymentMode").value("ci"))
            .andExpect(jsonPath("$.data.databaseDialect").value("h2"))
            .andExpect(jsonPath("$.data.migrationLocation").value("classpath:db/migration/h2"))
            .andExpect(jsonPath("$.data.activeProfiles", hasItem("test")))
            .andExpect(jsonPath("$.data.healthStatus").value("UP"))
            .andExpect(jsonPath("$.data.featureFlags[*].key", hasItems(
                "graph-projection",
                "search-projection",
                "dify-workflow",
                "audit-persistence",
                "external-provider",
                "domestic-crypto"
            )))
            .andExpect(jsonPath("$.data.dependencies[*].key", hasItems(
                "database",
                "prometheus",
                "backup-restore",
                "graph-projection",
                "search-projection",
                "dify-workflow",
                "model-gateway",
                "external-provider"
            )))
            .andExpect(jsonPath("$.data.dependencies[?(@.key=='graph-projection')].status", hasItem("NOT_CONNECTED")))
            .andExpect(jsonPath("$.data.dependencies[?(@.key=='search-projection')].status", hasItem("NOT_CONNECTED")))
            .andExpect(jsonPath("$.data.dependencies[?(@.key=='dify-workflow')].status", hasItem("MODEL_DISABLED")))
            .andExpect(jsonPath("$.data.dependencies[?(@.key=='model-gateway')].status", hasItem("MODEL_DISABLED")))
            .andExpect(jsonPath("$.data.dependencies[?(@.key=='external-provider')].status", hasItem("NOT_CONNECTED")))
            .andExpect(content().string(not(containsString("\"status\":\"DISABLED\""))))
            .andExpect(jsonPath("$.data.backup.checksumPolicy").value("SHA-256 摘要随备份文件生成，恢复前自动校验"))
            .andExpect(jsonPath("$.data.backup.drillEvidence.status").value("NOT_AVAILABLE"))
            .andExpect(jsonPath("$.data.backup.drillEvidence.detail").value("尚未提供隔离恢复演练证据"))
            .andExpect(jsonPath("$.data.domesticProfile.databaseVendors", hasItems("达梦", "人大金仓")))
            .andExpect(content().string(not(containsString("password"))))
            .andExpect(content().string(not(containsString("secret"))));
    }

    /**
     * 验证 actuator liveness/readiness 探针已经作为运行底座一等契约暴露。
     */
    @Test
    void actuatorLivenessAndReadinessAreExposed() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
