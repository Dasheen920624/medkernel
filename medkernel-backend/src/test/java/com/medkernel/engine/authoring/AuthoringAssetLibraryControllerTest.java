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
                .queryParam("assetType", "CONDITION_FRAGMENT")
                .queryParam("keyword", "CKD")
                .queryParam("tag", "复用")
                .queryParam("favoriteOnly", "true")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].assetType").value("CONDITION_FRAGMENT"))
            .andExpect(jsonPath("$.data.items[0].tags[0]").value("复用"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void profileEndpointSavesCategoryAndTags() throws Exception {
        when(service.updateProfile(
                eq(VersionedAssetType.CONDITION_FRAGMENT),
                eq("frag-ckd"),
                any(AuthoringAssetProfileRequest.class)))
            .thenReturn(new AuthoringAssetProfileResponse(
                VersionedAssetType.CONDITION_FRAGMENT,
                "frag-ckd",
                "慢病",
                List.of("复用", "CKD"),
                "trace-assets"));

        mvc.perform(put("/api/v1/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/profile")
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
        when(service.favorite(VersionedAssetType.CONDITION_FRAGMENT, "frag-ckd"))
            .thenReturn(new AuthoringAssetFavoriteResponse(
                VersionedAssetType.CONDITION_FRAGMENT,
                "frag-ckd",
                true,
                "trace-assets"));
        when(service.unfavorite(VersionedAssetType.CONDITION_FRAGMENT, "frag-ckd"))
            .thenReturn(new AuthoringAssetFavoriteResponse(
                VersionedAssetType.CONDITION_FRAGMENT,
                "frag-ckd",
                false,
                "trace-assets"));

        mvc.perform(post("/api/v1/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/favorite")
                .with(writeJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorite").value(true));

        mvc.perform(delete("/api/v1/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/favorite")
                .with(writeJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    void cloneEndpointReturnsDraftAsset() throws Exception {
        when(service.cloneAsset(
                eq(VersionedAssetType.CONDITION_FRAGMENT),
                eq("frag-ckd"),
                any(AuthoringAssetCloneRequest.class)))
            .thenReturn(new AuthoringAssetCloneResponse(
                VersionedAssetType.CONDITION_FRAGMENT,
                "frag-ckd",
                VersionedAssetType.CONDITION_FRAGMENT,
                "frag-ckd-copy",
                "FRAG.CKD.COPY",
                "DRAFT"));

        mvc.perform(post("/api/v1/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/clone")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "newCode": "FRAG.CKD.COPY",
                      "newName": "CKD 条件副本",
                      "newVersion": 1,
                      "packageVersion": "pkg-2026.06"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clonedAssetId").value("frag-ckd-copy"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
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
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-ckd",
            "FRAG.CKD",
            "CKD 条件片段",
            "慢病",
            List.of("复用", "CKD"),
            "1",
            "ACTIVE",
            "pkg-2026.06",
            true,
            true,
            Instant.parse("2026-06-08T00:00:00Z")
        );
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("asset-reader")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("doctor")))
            .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("asset-author")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("medical_affairs")))
            .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS"));
    }
}
