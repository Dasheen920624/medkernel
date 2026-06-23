package com.medkernel.engine.authoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthoringAssetLibraryControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthoringAssetLibraryService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listEndpointReturnsUnifiedAssets() throws Exception {
        when(service.list(any(AuthoringAssetLibraryQuery.class)))
            .thenReturn(PageResponse.of(List.of(item()), new PageRequest(0, 20, null), 1));

        mvc.perform(get("/api/v1/engine/authoring/assets")
                .queryParam("assetType", "RULE")
                .queryParam("keyword", "CKD")
                .queryParam("tag", "复用")
                .queryParam("favoriteOnly", "true")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].assetType").value("RULE"))
            .andExpect(jsonPath("$.data.items[0].tags[0]").value("复用"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listEndpointAllowsFollowupReadersForFollowupAssets() throws Exception {
        when(service.list(any(AuthoringAssetLibraryQuery.class)))
            .thenReturn(PageResponse.of(List.of(item()), new PageRequest(0, 20, null), 1));

        mvc.perform(get("/api/v1/engine/authoring/assets")
                .queryParam("assetType", "FOLLOWUP")
                .with(followupReadJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listEndpointAllowsRulePathwayReadersToQueryAllPermittedAssets() throws Exception {
        when(service.list(any(AuthoringAssetLibraryQuery.class)))
            .thenReturn(PageResponse.of(List.of(item()), new PageRequest(0, 20, null), 1));

        mvc.perform(get("/api/v1/engine/authoring/assets")
                .with(platformKnowledgeReadJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void profileEndpointSavesCategoryAndTags() throws Exception {
        when(service.updateProfile(
                eq(VersionedAssetType.RULE),
                eq("rule-ckd"),
                any(AuthoringAssetProfileRequest.class)))
            .thenReturn(new AuthoringAssetProfileResponse(
                VersionedAssetType.RULE,
                "rule-ckd",
                "慢病",
                List.of("复用", "CKD"),
                "trace-assets"));

        mvc.perform(put("/api/v1/engine/authoring/assets/RULE/rule-ckd/profile")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category": "慢病", "tags": ["复用", "CKD"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.category").value("慢病"))
            .andExpect(jsonPath("$.data.tags[1]").value("CKD"));
    }

    @Test
    void favoriteEndpointTogglesPersonalFavorite() throws Exception {
        when(service.favorite(VersionedAssetType.RULE, "rule-ckd"))
            .thenReturn(new AuthoringAssetFavoriteResponse(
                VersionedAssetType.RULE,
                "rule-ckd",
                true,
                "trace-assets"));
        when(service.unfavorite(VersionedAssetType.RULE, "rule-ckd"))
            .thenReturn(new AuthoringAssetFavoriteResponse(
                VersionedAssetType.RULE,
                "rule-ckd",
                false,
                "trace-assets"));

        mvc.perform(post("/api/v1/engine/authoring/assets/RULE/rule-ckd/favorite")
                .with(writeJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorite").value(true));

        mvc.perform(delete("/api/v1/engine/authoring/assets/RULE/rule-ckd/favorite")
                .with(writeJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    void listEndpointRejectsMissingReadPermission() throws Exception {
        mvc.perform(get("/api/v1/engine/authoring/assets")
                .with(jwt().jwt(token -> token
                        .subject("guest")
                        .claim("tenant_id", "tenant-A")
                        .claim("roles", List.of("guest")))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
            .andExpect(status().isForbidden());
    }

    private AuthoringAssetLibraryItem item() {
        return new AuthoringAssetLibraryItem(
            VersionedAssetType.RULE,
            "rule-ckd",
            "RULE.CKD",
            "CKD 阻断规则",
            "慢病",
            List.of("复用", "CKD"),
            "1",
            "ACTIVE",
            true,
            Instant.parse("2026-06-08T00:00:00Z")
        );
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("asset-reader")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("asset-author")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }

    private static RequestPostProcessor followupReadJwt() {
        return jwt().jwt(token -> token
                .subject("clinical-user")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private static RequestPostProcessor platformKnowledgeReadJwt() {
        return jwt().jwt(token -> token
                .subject("engine-operator")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }
}
