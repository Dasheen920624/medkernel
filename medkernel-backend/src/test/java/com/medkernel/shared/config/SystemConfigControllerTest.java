package com.medkernel.shared.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.bootstrap.MfaSecretCodec;
import com.medkernel.engine.security.auth.JwtIssuer;
import com.medkernel.shared.runtime.RuntimeOperationsService;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.crypto.spec.SecretKeySpec;

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
    private static final String EXTERNAL_PROVIDER_FLAG_KEY = "medkernel.runtime.feature-flags.external-provider.enabled";
    private static final String BACKUP_ENABLED_KEY = "medkernel.runtime.backup.enabled";
    private static final String BACKUP_RPO_KEY = "medkernel.runtime.backup.rpo";
    private static final String BACKUP_RTO_KEY = "medkernel.runtime.backup.rto";
    private static final String JWT_TTL_KEY = "medkernel.auth.jwt.ttl-seconds";
    private static final String AUTH_MODE_KEY = "medkernel.auth.mode";
    private static final String MFA_ENABLED_KEY = "medkernel.auth.mfa.enabled";
    private static final String AUTH_PASSWORD_MIN_LENGTH_KEY = "medkernel.auth.password.min-length";
    private static final String KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY =
        "medkernel.knowledge.literature.material-root-uri";
    private static final String KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_DEFAULT = "";
    private static final String KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY =
        "medkernel.knowledge.production.p6-independent-acceptance";
    private static final String LOG_LEVEL_KEY = "medkernel.logging.level.com.medkernel";
    private static final String DEV_SECRET = "medkernel-dev-secret-please-change-at-least-32-bytes";
    private static final String MFA_USER = "it-ops-1";

    @Autowired
    MockMvc mvc;

    @Autowired
    RuntimeOperationsService runtimeOperationsService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtIssuer jwtIssuer;

    @Autowired
    PlatformCredentialRepository credentialRepository;

    @Autowired
    MfaSecretCodec mfaSecretCodec;

    @Autowired
    LoggingSystem loggingSystem;

    @BeforeEach
    void seedMfaCredential() {
        seedMfaCredential(MFA_USER);
    }

    @AfterEach
    void restoreSeededRuntimeFlags() {
        credentialRepository.findByTenantIdAndUserId("t-1", MFA_USER)
            .ifPresent(credentialRepository::delete);
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
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'false', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, EXTERNAL_PROVIDER_FLAG_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key IN (?, ?, ?, ?)",
            GRAPH_FLAG_KEY, AUDIT_FLAG_KEY, DOMESTIC_CRYPTO_FLAG_KEY, EXTERNAL_PROVIDER_FLAG_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_item WHERE tenant_id = 'SYSTEM' AND config_key = ?",
            MFA_ENABLED_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'false', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, BACKUP_ENABLED_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '未启用', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key IN (?, ?)
            """, BACKUP_RPO_KEY, BACKUP_RTO_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key IN (?, ?, ?)",
            BACKUP_ENABLED_KEY, BACKUP_RPO_KEY, BACKUP_RTO_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '28800', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, JWT_TTL_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'PLATFORM', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, AUTH_MODE_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '12', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, AUTH_PASSWORD_MIN_LENGTH_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?, source = 'PLATFORM_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_DEFAULT, KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key = ?", KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'DEBUG', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, LOG_LEVEL_KEY);
        jdbcTemplate.update("DELETE FROM mk_config_history WHERE config_key IN (?, ?)", JWT_TTL_KEY, LOG_LEVEL_KEY);
        loggingSystem.setLogLevel("com.medkernel", LogLevel.DEBUG);
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
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
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
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
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void backupPolicyIsBackedByConfigCenterWithoutRestart() throws Exception {
        assertThat(runtimeOperationsService.snapshot().backup().enabled()).isFalse();

        patchConfig(BACKUP_ENABLED_KEY, "true", "验证备份策略热生效");
        patchConfig(BACKUP_RPO_KEY, "30 分钟", "验证备份 RPO 热生效");
        patchConfig(BACKUP_RTO_KEY, "2 小时", "验证备份 RTO 热生效");

        assertThat(runtimeOperationsService.snapshot().backup().enabled()).isTrue();
        assertThat(runtimeOperationsService.snapshot().backup().rpo()).isEqualTo("30 分钟");
        assertThat(runtimeOperationsService.snapshot().backup().rto()).isEqualTo("2 小时");

        Integer historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mk_config_history WHERE config_key IN (?, ?, ?)",
            Integer.class,
            BACKUP_ENABLED_KEY,
            BACKUP_RPO_KEY,
            BACKUP_RTO_KEY);
        assertThat(historyCount).isEqualTo(3);
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void jwtTtlIsBackedByConfigCenterWithoutRestart() throws Exception {
        patchHighRiskConfig(JWT_TTL_KEY, "120", "验证 JWT TTL 热生效");

        JwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(DEV_SECRET.getBytes(), "HmacSHA256"))
            .build();
        Jwt jwt = decoder.decode(jwtIssuer.issue("doctor-1", "t-1", java.util.List.of("clinical-user")));

        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(120);
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void authModeIsBackedByConfigCenterAndRejectsInvalidValue() throws Exception {
        mvc.perform(get("/api/v1/system/configs")
                .queryParam("prefix", "medkernel.auth."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.key=='medkernel.auth.mode')].value").value(hasItem("PLATFORM")))
            .andExpect(jsonPath("$.data[?(@.key=='medkernel.auth.mode')].protectedConfig").value(hasItem(true)));

        mvc.perform(patch("/api/v1/system/configs/{key}", AUTH_MODE_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "LEGACY",
                      "reason": "验证认证模式枚举约束",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));

        assertThat(configValue(AUTH_MODE_KEY)).isEqualTo("PLATFORM");
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void passwordMinLengthCannotBeWeakenedBelowStrongBaseline() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", AUTH_PASSWORD_MIN_LENGTH_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "8",
                      "reason": "验证强密码最小长度不能降级"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));

        assertThat(configValue(AUTH_PASSWORD_MIN_LENGTH_KEY)).isEqualTo("12");
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void knowledgeLiteratureMaterialRootUriRequiresManagedStorageConfiguration() throws Exception {
        mvc.perform(get("/api/v1/system/configs")
                .queryParam("prefix", "medkernel.knowledge.literature."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.key=='%s')].value".formatted(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
                .value(hasItem(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_DEFAULT)))
            .andExpect(jsonPath("$.data[?(@.key=='%s')].displayName".formatted(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
                .value(hasItem("平台知识文献资料库根地址")))
            .andExpect(jsonPath("$.data[?(@.key=='%s')].source".formatted(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
                .value(hasItem("PLATFORM_SEED")))
            .andExpect(jsonPath("$.data[?(@.key=='%s')].protectedConfig".formatted(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
                .value(hasItem(true)));

        mvc.perform(patch("/api/v1/system/configs/{key}", KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "file:///tmp/medkernel-knowledge",
                      "reason": "验证知识文献资料目录禁止指向 tmp",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));

        assertThat(configValue(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
            .isEqualTo(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_DEFAULT);

        String yearlyManagedStorageUri =
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/2026/";
        mvc.perform(patch("/api/v1/system/configs/{key}", KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "%s",
                      "reason": "正式文献资料库按年度分层，现场使用受管本地磁盘",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """.formatted(yearlyManagedStorageUri)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value(yearlyManagedStorageUri))
            .andExpect(jsonPath("$.data.source").value("API"))
            .andExpect(jsonPath("$.data.version").value(2));

        assertThat(configValue(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
            .isEqualTo(yearlyManagedStorageUri);

        mvc.perform(post("/api/v1/system/configs/{key}/rollback",
                KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "演练回滚到未配置状态",
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value(""))
            .andExpect(jsonPath("$.data.version").value(3));

        assertThat(configValue(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY)).isEmpty();
    }

    @Test
    void obsoleteP6IndependentAcceptanceIsNotSeededAsRuntimeConfiguration() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mk_config_item WHERE config_key = ?",
            Integer.class,
            KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY);

        assertThat(count).isZero();
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void nullableUnconfiguredValueIsReturnedAsEmptyString() throws Exception {
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = NULL
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY);

        mvc.perform(get("/api/v1/system/configs")
                .queryParam("prefix", "medkernel.knowledge.literature."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].key")
                .value(KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY))
            .andExpect(jsonPath("$.data[0].value").value(""));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void loggingLevelIsAppliedFromConfigCenterWithoutRestart() throws Exception {
        patchConfig(LOG_LEVEL_KEY, "WARN", "验证日志级别热生效");

        assertThat(loggingSystem.getLoggerConfiguration("com.medkernel").getConfiguredLevel())
            .isEqualTo(LogLevel.WARN);
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void highRiskFeatureFlagRequiresSecondConfirmationBeforeUpdate() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "true",
                      "reason": "验证高危配置必须二次确认",
                      "expectedVersion": 1
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-CONFIG-002"));

        assertThat(configValue(EXTERNAL_PROVIDER_FLAG_KEY)).isEqualTo("false");

        mvc.perform(patch("/api/v1/system/configs/{key}", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "true",
                      "reason": "已完成高危配置影响确认",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.key").value(EXTERNAL_PROVIDER_FLAG_KEY))
            .andExpect(jsonPath("$.data.value").value("true"))
            .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void defaultDisabledMfaAllowsConfirmedHighRiskUpdateWithoutMfaBoundUser() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(itOpsWithoutMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "true",
                      "reason": "默认关闭 MFA 时确认高危配置影响",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(configValue(EXTERNAL_PROVIDER_FLAG_KEY)).isEqualTo("true");
    }

    @Test
    void enabledMfaStillRequiresVerifiedSessionForSystemSuperAdmin() throws Exception {
        upsertMfaEnabled(true);

        mvc.perform(patch("/api/v1/system/configs/{key}", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(systemSuperAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "true",
                      "reason": "显式开启 MFA 后内置超管也必须完成验证",
                      "expectedVersion": 1,
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-010"));

        assertThat(configValue(EXTERNAL_PROVIDER_FLAG_KEY)).isEqualTo("false");
    }

    private void upsertMfaEnabled(boolean enabled) {
        int updated = jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?, updated_by = 'test'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, Boolean.toString(enabled), MFA_ENABLED_KEY);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, description, source, protected_flag, active_flag,
                version, created_at, created_by, updated_at, updated_by
            ) VALUES (
                'cfg-test-mfa-enabled', 'SYSTEM', ?, ?, 'BOOLEAN', '登录 MFA',
                'HIGH', '平台管理员', '测试 MFA 运行策略。', 'YML_SEED', 'Y', 'Y',
                1, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'
            )
            """, MFA_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void configRollbackRestoresPreviousValueAndWritesRollbackHistory() throws Exception {
        patchConfig(GRAPH_FLAG_KEY, "true", "验证配置回滚前置变更");
        assertThat(runtimeFlag("graph-projection").enabled()).isTrue();

        mvc.perform(post("/api/v1/system/configs/{key}/rollback", GRAPH_FLAG_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "回滚到上一版本"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.key").value(GRAPH_FLAG_KEY))
            .andExpect(jsonPath("$.data.value").value("false"))
            .andExpect(jsonPath("$.data.version").value(3));

        assertThat(runtimeFlag("graph-projection").enabled()).isFalse();
        Integer rollbackCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM mk_config_history
             WHERE config_key = ? AND change_type = 'ROLLBACK'
               AND before_value = 'true' AND after_value = 'false'
            """,
            Integer.class,
            GRAPH_FLAG_KEY);
        assertThat(rollbackCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void highRiskRollbackRequiresSecondConfirmation() throws Exception {
        patchHighRiskConfig(EXTERNAL_PROVIDER_FLAG_KEY, "true", "先开启高危配置以验证回滚确认");

        mvc.perform(post("/api/v1/system/configs/{key}/rollback", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "缺少二次确认的高危回滚"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-CONFIG-002"));

        assertThat(configValue(EXTERNAL_PROVIDER_FLAG_KEY)).isEqualTo("true");

        mvc.perform(post("/api/v1/system/configs/{key}/rollback", EXTERNAL_PROVIDER_FLAG_KEY)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "已确认高危配置回滚影响",
                      "confirmedHighRisk": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value("false"))
            .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
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
    void systemSuperAdminCannotDisableAuditPersistenceFromConfigCenter() throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", AUDIT_FLAG_KEY)
                .with(systemSuperAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "false",
                      "reason": "内置超管也不能关闭审计持久化"
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
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
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

    private String configValue(String key) {
        return jdbcTemplate.queryForObject(
            "SELECT config_value FROM mk_config_item WHERE tenant_id = 'SYSTEM' AND config_key = ?",
            String.class,
            key);
    }

    private void patchConfig(String key, String value, String reason) throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "%s",
                      "reason": "%s"
                    }
                    """.formatted(value, reason)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.key").value(key))
            .andExpect(jsonPath("$.data.value").value(value))
            .andExpect(jsonPath("$.data.source").value("API"));
    }

    private void patchHighRiskConfig(String key, String value, String reason) throws Exception {
        mvc.perform(patch("/api/v1/system/configs/{key}", key)
                .with(itOpsWithMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "%s",
                      "reason": "%s",
                      "confirmedHighRisk": true
                    }
                    """.formatted(value, reason)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor itOpsWithMfa() {
        return jwtFor(MFA_USER);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor itOpsWithoutMfa() {
        return jwtFor("it-ops-no-mfa");
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor systemSuperAdmin() {
        return SecurityMockMvcRequestPostProcessors.jwt()
            .jwt(t -> t.subject("system-superadmin-1").claim("tenant_id", "t-1"))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN"));
    }

    private void seedMfaCredential(String userId) {
        if (credentialRepository.findByTenantIdAndUserId("t-1", userId).isPresent()) {
            return;
        }
        java.time.Instant now = java.time.Instant.now();
        credentialRepository.save(new PlatformCredential(
            null,
            "cred-" + userId,
            "t-1",
            userId,
            userId,
            "$2a$10$hash",
            "ACTIVE",
            "N",
            mfaSecretCodec.encode("JBSWY3DPEHPK3PXP", "Recovery@2026"),
            now,
            "test",
            now,
            "test",
            "trace-test"));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(String userId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
            .jwt(t -> t.subject(userId).claim("tenant_id", "t-1"))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
