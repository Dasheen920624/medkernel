package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PackageEngineControllerSecurityTest {

    private static final String PKG_ROOT = "/api/v1/engine/pkg/packages";
    private static final String OLD_ROOT = "/api/v1/engine/packages";

    private static final String CREATE_BODY = """
        {
          "packageCode": "PKG.COPD",
          "packageVersion": "1.0.0",
          "name": "慢阻肺专病包",
          "description": "慢阻肺资产包"
        }
        """;

    private static final String ITEM_BODY = """
        {
          "assetType": "RULE",
          "assetId": "rule-1",
          "assetVersion": "1"
        }
        """;

    private static final String OFFLINE_IMPORT_BODY = """
        {"offlinePackageJson":"{\\"format\\":\\"MEDKERNEL_PACKAGE_OFFLINE_V2\\"}"}
        """;

    private static final String SYNC_BODY = """
        {
          "targetOrgUnitId": "org-hosp-1",
          "strategy": "GRAYSCALE",
          "scopeType": "DEPARTMENT",
          "scopeValue": "dept-1",
          "reason": "验证配置包灰度发布",
          "adapterIds": ["adapter-dify-1"]
        }
        """;

    private static final String ROLLBACK_BODY = """
        {
          "targetPackageId": "pkg-2",
          "confirmedCurrentVersion": "2.0.0",
          "confirmedTargetVersion": "1.0.0",
          "reason": "临床专家已确认回滚窗口",
          "confirmedHighRisk": true
        }
        """;

    private static String standardContextFields(String tenantId) {
        return """
              "request_id": "req-pkg-1",
              "trace_id": "trace-pkg-1",
              "tenant_id": "%s",
              "group_id": "group-A",
              "hospital_id": "hospital-A",
              "campus_id": "campus-A",
              "site_id": "site-A",
              "department_id": "dept-A",
              "specialty_id": "specialty-A",
              "user_id": "tester",
              "role_codes": ["it-ops"],
              "package_version": "1.0.0"
            """.formatted(tenantId);
    }

    private static String createBodyWithContext(String tenantId) {
        return """
            {
              %s,
              "packageCode": "PKG.COPD",
              "packageVersion": "1.0.0",
              "name": "慢阻肺专病包",
              "description": "慢阻肺资产包"
            }
            """.formatted(standardContextFields(tenantId));
    }

    private static String operationBodyWithContext(String tenantId) {
        return """
            {
              %s
            }
            """.formatted(standardContextFields(tenantId));
    }

    private static String syncBodyWithContext(String tenantId) {
        return """
            {
              %s,
              "targetOrgUnitId": "org-hosp-1",
              "strategy": "GRAYSCALE",
              "scopeType": "DEPARTMENT",
              "scopeValue": "dept-1",
              "reason": "验证配置包灰度发布",
          "adapterIds": ["adapter-dify-1"]
            }
            """.formatted(standardContextFields(tenantId));
    }

    private static String instantiateBodyWithContext(String tenantId) {
        return """
            {
              %s,
              "packageCode": "PKG.FIRST.RUN",
              "packageVersion": "2026.06.03",
              "name": "首发配置包",
              "description": "由试点首发模板实例化"
            }
            """.formatted(standardContextFields(tenantId));
    }

    @Autowired
    MockMvc mvc;

    @MockBean
    PackageEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void authorizedUserButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post(PKG_ROOT)
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get(PKG_ROOT))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get(PKG_ROOT + "/pkg-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get(PKG_ROOT + "/pkg-1/diff/export"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get(PKG_ROOT + "/pkg-1/offline/export")
                .param("targetOrgUnitId", "hospital-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post(PKG_ROOT + "/offline/import")
                .contentType("application/json")
                .content(OFFLINE_IMPORT_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post(PKG_ROOT + "/pkg-1/items")
                .contentType("application/json")
                .content(ITEM_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotPublishOrRollbackPackage() throws Exception {
        mvc.perform(post(PKG_ROOT)
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post(PKG_ROOT + "/pkg-1/items")
                .contentType("application/json")
                .content(ITEM_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post(PKG_ROOT + "/pkg-1/sync")
                .contentType("application/json")
                .content(SYNC_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post(PKG_ROOT + "/pkg-1/rollback")
                .contentType("application/json")
                .content(ROLLBACK_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post(PKG_ROOT + "/pilot-templates/TPL.FIRST_RUN/instantiate")
                .contentType("application/json")
                .content(instantiateBodyWithContext("tenant-A")))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadPackages() throws Exception {
        mvc.perform(get(PKG_ROOT))
            .andExpect(status().isForbidden());

        mvc.perform(get(PKG_ROOT + "/pilot-templates"))
            .andExpect(status().isForbidden());

        mvc.perform(get(PKG_ROOT + "/asset-readiness"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void oldPackagesCustomerRootIsRemoved() throws Exception {
        mvc.perform(get(OLD_ROOT))
            .andExpect(status().isNotFound());

        mvc.perform(post(OLD_ROOT + "/pkg-1/sync")
                .contentType("application/json")
                .content(SYNC_BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void packageWriteRequestsRejectMissingStandardContext() throws Exception {
        mvc.perform(post(PKG_ROOT)
                .contentType("application/json")
                .content(CREATE_BODY)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.detail").value(containsString("统一入参")));

        mvc.perform(post(PKG_ROOT + "/pkg-1/items")
                .contentType("application/json")
                .content(ITEM_BODY)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.detail").value(containsString("统一入参")));

        mvc.perform(post(PKG_ROOT + "/pkg-1/sync")
                .contentType("application/json")
                .content(SYNC_BODY)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.detail").value(containsString("统一入参")));

        mvc.perform(post(PKG_ROOT + "/pkg-1/rollback")
                .contentType("application/json")
                .content(ROLLBACK_BODY)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.detail").value(containsString("统一入参")));

        mvc.perform(post(PKG_ROOT + "/pilot-templates/TPL.FIRST_RUN/instantiate")
                .contentType("application/json")
                .content("""
                    {
                      "packageCode": "PKG.FIRST.RUN",
                      "packageVersion": "2026.06.03",
                      "name": "首发配置包"
                    }
                    """)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.detail").value(containsString("统一入参")));
    }

    @Test
    void packageWriteRequestsRejectMismatchedContextTenant() throws Exception {
        mvc.perform(post(PKG_ROOT)
                .contentType("application/json")
                .content(createBodyWithContext("tenant-B"))
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-BASE-004"))
            .andExpect(jsonPath("$.detail").value(containsString("租户不一致")));
    }

    @Test
    void authorizedUserCanValidateReleaseAndReadPersistedSyncLogs() throws Exception {
        when(service.validatePackage("pkg-1"))
            .thenReturn(new PackageValidateResponse(
                "pkg-1", KnowledgePackageStatus.DRAFT, 1, "a".repeat(64), true, List.of()));
        when(service.releasePackage(eq("pkg-1"), any(PackageSyncRequest.class)))
            .thenReturn(new PackageSyncResponse(
                "plan-1", "pkg-1", ReleasePlanStatus.NOT_SYNCED,
                List.of(new SyncLogResponse(
                    "log-1", "plan-1", "target-dify-1", SyncLogStatus.NOT_SYNCED,
                    "NOT_SYNCED", "未配置真实同步适配器", 0, null))));
        when(service.listSyncLogs("pkg-1"))
            .thenReturn(List.of(new SyncLogResponse(
                "log-1", "plan-1", "target-dify-1", SyncLogStatus.NOT_SYNCED,
                "NOT_SYNCED", "未配置真实同步适配器", 0, null)));

        mvc.perform(post(PKG_ROOT + "/pkg-1/validate")
                .contentType("application/json")
                .content(operationBodyWithContext("tenant-A"))
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.itemCount").value(1));

        mvc.perform(post(PKG_ROOT + "/pkg-1/release")
                .contentType("application/json")
                .content(syncBodyWithContext("tenant-A"))
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("NOT_SYNCED"))
            .andExpect(jsonPath("$.data.logs[0].syncEvidence").doesNotExist());

        mvc.perform(get(PKG_ROOT + "/pkg-1/sync-logs")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("NOT_SYNCED"))
            .andExpect(jsonPath("$.data[0].errorCode").value("NOT_SYNCED"));
    }

    @Test
    void authorizedUserCanReadTemplatesReadinessAndInstantiatePilotTemplate() throws Exception {
        when(service.listPilotTemplates())
            .thenReturn(List.of(new PilotPackageTemplateResponse(
                "tpl-first-run",
                "TPL.FIRST_RUN",
                "t-1",
                "试点首发最小可运行集",
                "平台首发模板",
                "PKG.PILOT",
                "1.0.0",
                1,
                List.of(new PilotPackageTemplateItemResponse(
                    VersionedAssetType.RULE,
                    "rule-stable",
                    "2",
                    true,
                    10,
                    "首发规则"
                ))
            )));
        when(service.getAssetReadiness())
            .thenReturn(new PackageAssetReadinessResponse(
                "tenant-A",
                true,
                1,
                1,
                1,
                1,
                true,
                "pkg-active",
                List.of(),
                Instant.parse("2026-06-03T00:00:00Z")
            ));
        when(service.instantiatePilotTemplate(eq("TPL.FIRST_RUN"), any(PilotPackageTemplateInstantiateRequest.class)))
            .thenReturn(new PilotPackageInstantiationResponse(
                "TPL.FIRST_RUN",
                new PackageResponse(
                    "pkg-first-run",
                    "PKG.FIRST.RUN",
                    "2026.06.03",
                    "首发配置包",
                    "由试点首发模板实例化",
                    KnowledgePackageStatus.DRAFT
                ),
                List.of(new PackageItemResponse(
                    "item-rule",
                    "pkg-first-run",
                    VersionedAssetType.RULE,
                    "rule-stable",
                    "2"
                ))
            ));

        mvc.perform(get(PKG_ROOT + "/pilot-templates")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].templateCode").value("TPL.FIRST_RUN"))
            .andExpect(jsonPath("$.data[0].items[0].assetType").value("RULE"));

        mvc.perform(get(PKG_ROOT + "/asset-readiness")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.readyPackageId").value("pkg-active"));

        mvc.perform(post(PKG_ROOT + "/pilot-templates/TPL.FIRST_RUN/instantiate")
                .contentType("application/json")
                .content(instantiateBodyWithContext("tenant-A"))
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.templateCode").value("TPL.FIRST_RUN"))
            .andExpect(jsonPath("$.data.packageInfo.packageId").value("pkg-first-run"))
            .andExpect(jsonPath("$.data.items[0].assetId").value("rule-stable"));
    }

    @Test
    void authorizedUserCanDownloadDiffEvidenceNdjson() throws Exception {
        when(service.exportDiffEvidence("pkg-1", "pkg-base"))
            .thenReturn("{\"event\":\"PACKAGE_DIFF_SUMMARY\"}\n");

        mvc.perform(get(PKG_ROOT + "/pkg-1/diff/export")
                .param("basePackageId", "pkg-base")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/x-ndjson;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", containsString("package-diff-pkg-1.jsonl")))
            .andExpect(content().string(containsString("PACKAGE_DIFF_SUMMARY")));
    }

    @Test
    void authorizedUserCanDownloadOfflinePackageJson() throws Exception {
        when(service.exportOfflinePackage("pkg-1", "hospital-1"))
            .thenReturn("{\"format\":\"MEDKERNEL_PACKAGE_OFFLINE_V2\",\"manifest\":{\"payloadSha256\":\"abc\"}}\n");

        mvc.perform(get(PKG_ROOT + "/pkg-1/offline/export")
                .param("targetOrgUnitId", "hospital-1")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", containsString("package-offline-pkg-1.json")))
            .andExpect(content().string(containsString("MEDKERNEL_PACKAGE_OFFLINE_V2")))
            .andExpect(content().string(containsString("payloadSha256")));
    }

    @Test
    void authorizedUserCanDownloadSyncEvidenceNdjson() throws Exception {
        when(service.exportSyncEvidence("pkg-1"))
            .thenReturn("{\"event\":\"PACKAGE_SYNC_EVIDENCE_SUMMARY\",\"failedAdapterCount\":1}\n");

        mvc.perform(get(PKG_ROOT + "/pkg-1/sync-logs/export")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/x-ndjson;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", containsString("package-sync-evidence-pkg-1.jsonl")))
            .andExpect(content().string(containsString("PACKAGE_SYNC_EVIDENCE_SUMMARY")))
            .andExpect(content().string(containsString("failedAdapterCount")));
    }

    @Test
    void authorizedUserCanImportOfflinePackageJson() throws Exception {
        when(service.importOfflinePackage(new PackageOfflineImportRequest("{\"format\":\"MEDKERNEL_PACKAGE_OFFLINE_V2\"}")))
            .thenReturn(new PackageOfflineImportResponse(
                "pkg-imported", "PKG.IMPORT", "2026.06.01", KnowledgePackageStatus.DRAFT, 2, "a".repeat(64)));

        mvc.perform(post(PKG_ROOT + "/offline/import")
                .contentType("application/json")
                .content(OFFLINE_IMPORT_BODY)
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.packageId").value("pkg-imported"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.itemCount").value(2))
            .andExpect(jsonPath("$.data.payloadSha256").value("a".repeat(64)));
    }
}
