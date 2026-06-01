package com.medkernel.engine.pkg;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private static final String SYNC_BODY = """
        {
          "targetOrgUnitId": "org-hosp-1",
          "strategy": "GRAYSCALE",
          "scopeType": "DEPARTMENT",
          "scopeValue": "dept-1",
          "targetIds": ["target-dify-1"]
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
        mvc.perform(post("/api/v1/engine/packages")
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/packages"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/packages/pkg-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/packages/pkg-1/diff/export"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/packages/pkg-1/offline/export"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/packages/pkg-1/items")
                .contentType("application/json")
                .content(ITEM_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotPublishOrRollbackPackage() throws Exception {
        mvc.perform(post("/api/v1/engine/packages")
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/packages/pkg-1/items")
                .contentType("application/json")
                .content(ITEM_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/packages/pkg-1/sync")
                .contentType("application/json")
                .content(SYNC_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/packages/pkg-1/rollback")
                .contentType("application/json")
                .content(ROLLBACK_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadPackages() throws Exception {
        mvc.perform(get("/api/v1/engine/packages"))
            .andExpect(status().isForbidden());
    }

    @Test
    void authorizedUserCanDownloadDiffEvidenceNdjson() throws Exception {
        when(service.exportDiffEvidence("pkg-1", "pkg-base"))
            .thenReturn("{\"event\":\"PACKAGE_DIFF_SUMMARY\"}\n");

        mvc.perform(get("/api/v1/engine/packages/pkg-1/diff/export")
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
        when(service.exportOfflinePackage("pkg-1"))
            .thenReturn("{\"format\":\"MEDKERNEL_PACKAGE_OFFLINE_V1\",\"manifest\":{\"payloadSha256\":\"abc\"}}\n");

        mvc.perform(get("/api/v1/engine/packages/pkg-1/offline/export")
                .with(jwt()
                    .jwt(token -> token.subject("tester").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", containsString("package-offline-pkg-1.json")))
            .andExpect(content().string(containsString("MEDKERNEL_PACKAGE_OFFLINE_V1")))
            .andExpect(content().string(containsString("payloadSha256")));
    }
}
