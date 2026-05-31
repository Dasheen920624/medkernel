package com.medkernel.shared.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.runtime.RuntimeOperationsService;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 CONFIG-01 配置中心的存储、元数据与运行底座热生效合同。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemConfigControllerTest {

    private static final String GRAPH_FLAG_KEY = "medkernel.runtime.feature-flags.graph-projection.enabled";
    private static final String AUDIT_FLAG_KEY = "medkernel.runtime.feature-flags.audit-persistence.enabled";
    private static final String DOMESTIC_CRYPTO_FLAG_KEY = "medkernel.runtime.feature-flags.domestic-crypto.enabled";

    @Autowired
    MockMvc mvc;

    @Autowired
    RuntimeOperationsService runtimeOperationsService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreSeededRuntimeFlags() {
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'false', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, GRAPH_FLAG_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'true', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key IN (?, ?)
            """, AUDIT_FLAG_KEY, DOMESTIC_CRYPTO_FLAG_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key IN (?, ?, ?)",
            GRAPH_FLAG_KEY, AUDIT_FLAG_KEY, DOMESTIC_CRYPTO_FLAG_KEY);
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void runtimeFeatureFlagIsBackedByConfigCenterWithoutRestart() throws Exception {
        assertThat(runtimeFlag("graph-projection").enabled()).isFalse();

        mvc.perform(patch("/api/v1/system/configs/{key}", GRAPH_FLAG_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "true",
                      "reason": "验证配置中心热生效"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.key").value(GRAPH_FLAG_KEY))
            .andExpect(jsonPath("$.data.value").value("true"))
            .andExpect(jsonPath("$.data.source").value("API"))
            .andExpect(jsonPath("$.data.version").value(2));

        assertThat(runtimeFlag("graph-projection").enabled()).isTrue();
        Integer historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mk_config_history WHERE config_key = ?",
            Integer.class,
            GRAPH_FLAG_KEY);
        assertThat(historyCount).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void configListExposesSeededFeatureFlagMetadata() throws Exception {
        mvc.perform(get("/api/v1/system/configs")
                .queryParam("prefix", "medkernel.runtime.feature-flags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[?(@.key=='medkernel.runtime.feature-flags.audit-persistence.enabled')].risk")
                .value(hasItem("HIGH")))
            .andExpect(jsonPath("$.data[?(@.key=='medkernel.runtime.feature-flags.audit-persistence.enabled')].protectedConfig")
                .value(hasItem(true)))
            .andExpect(jsonPath("$.data[?(@.key=='medkernel.runtime.feature-flags.graph-projection.enabled')].source")
                .value(hasItem("YML_SEED")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void auditPersistenceFeatureFlagCannotBeDisabledFromConfigCenter() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", AUDIT_FLAG_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "false",
                      "reason": "验证高危配置护栏"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUDIT-001"));

        String value = jdbcTemplate.queryForObject(
            "SELECT config_value FROM mk_config_item WHERE tenant_id = 'SYSTEM' AND config_key = ?",
            String.class,
            AUDIT_FLAG_KEY);
        assertThat(value).isEqualTo("true");
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void domesticCryptoFeatureFlagCannotBeDisabledFromConfigCenter() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", DOMESTIC_CRYPTO_FLAG_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "false",
                      "reason": "验证国密高危配置护栏"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-CONFIG-001"));

        String value = jdbcTemplate.queryForObject(
            "SELECT config_value FROM mk_config_item WHERE tenant_id = 'SYSTEM' AND config_key = ?",
            String.class,
            DOMESTIC_CRYPTO_FLAG_KEY);
        assertThat(value).isEqualTo("true");
    }

    private RuntimeFeatureFlag runtimeFlag(String key) {
        return runtimeOperationsService.snapshot().featureFlags().stream()
            .filter(flag -> key.equals(flag.key()))
            .findFirst()
            .orElseThrow();
    }
}
