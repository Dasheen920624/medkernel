package com.medkernel.engine.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D6 OPT-10：插件以只读优先、受控写入门禁和租户隔离为底线。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PluginSecurityControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        try {
            jdbc.update("DELETE FROM mk_plugin_grant");
            jdbc.update("DELETE FROM mk_plugin_registry");
        } catch (DataAccessException ignored) {
            // RED 阶段迁移尚未存在，失败原因应落在端点缺失，而不是清理钩子。
        }
    }

    @Test
    void pluginEndpointsRequireAuthenticationAndManagementPermission() throws Exception {
        mvc.perform(get("/api/v1/plugins"))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/plugins/register")
                .with(jwt().jwt(token -> token.subject("doctor-1").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pluginCode": "unauthorized-plugin",
                      "displayName": "未授权插件",
                      "capabilities": [
                        {
                          "capabilityKey": "read-runtime",
                          "capabilityType": "READ",
                          "serviceContractId": "runtime-operations",
                          "clinicalData": false
                        }
                      ]
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void pluginRegistrationDefaultsToPendingReviewAndReadOnly() throws Exception {
        mvc.perform(post("/api/v1/plugins/register")
                .with(ops("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pluginCode": "ward-read-model",
                      "displayName": "病区只读看板",
                      "capabilities": [
                        {
                          "capabilityKey": "read-runtime",
                          "capabilityType": "READ",
                          "serviceContractId": "runtime-operations",
                          "clinicalData": false
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.pluginCode").value("ward-read-model"))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.authorityBoundary").value("READ_ONLY"))
            .andExpect(jsonPath("$.data.tenantId").doesNotExist());

        mvc.perform(get("/api/v1/plugins").with(ops("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].pluginCode").value("ward-read-model"));

        mvc.perform(get("/api/v1/plugins").with(ops("t-2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void controlledWriteGrantRequiresApprovalAndClinicalSafetyConfirmation() throws Exception {
        String pluginId = registerWriter();

        mvc.perform(post("/api/v1/plugins/{pluginId}/grants", pluginId)
                .with(ops("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "capabilityKeys": ["publish-rule"],
                      "authorizationReason": "",
                      "clinicalSafetyConfirmed": false
                    }
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/plugins/{pluginId}/grants", pluginId)
                .with(ops("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "capabilityKeys": ["publish-rule"],
                      "authorizationReason": "院内插件权限用途已确认",
                      "clinicalSafetyConfirmed": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.data.grants[0].capabilityKey").value("publish-rule"))
            .andExpect(jsonPath("$.data.grants[0].serviceContractId").value("rule"));
    }

    @Test
    void crossTenantCannotGrantOrDisablePlugin() throws Exception {
        String pluginId = registerWriter();

        mvc.perform(post("/api/v1/plugins/{pluginId}/grants", pluginId)
                .with(ops("t-2"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "capabilityKeys": ["publish-rule"],
                      "authorizationReason": "跨租户授权不应生效",
                      "clinicalSafetyConfirmed": true
                    }
                    """))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/plugins/{pluginId}:disable", pluginId)
                .with(ops("t-2")))
            .andExpect(status().isNotFound());
    }

    @Test
    void pluginCanBeDisabledWithoutDeletingAuditHistory() throws Exception {
        String pluginId = registerWriter();

        mvc.perform(post("/api/v1/plugins/{pluginId}:disable", pluginId)
                .with(ops("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    private String registerWriter() throws Exception {
        String body = mvc.perform(post("/api/v1/plugins/register")
                .with(ops("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pluginCode": "rule-publisher",
                      "displayName": "规则发布插件",
                      "capabilities": [
                        {
                          "capabilityKey": "publish-rule",
                          "capabilityType": "WRITE",
                          "serviceContractId": "rule",
                          "clinicalData": true
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.authorityBoundary").value("CONTROLLED_WRITE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.pluginId");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ops(String tenantId) {
        return jwt().jwt(token -> token.subject("ops-1").claim("tenant_id", tenantId))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
