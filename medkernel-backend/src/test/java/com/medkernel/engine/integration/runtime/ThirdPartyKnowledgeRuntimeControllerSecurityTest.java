package com.medkernel.engine.integration.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ThirdPartyKnowledgeRuntimeControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean ThirdPartyKnowledgeRuntimeService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void anonymousCannotResolveCurrentRuntimeRelease() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/knowledge-runtime/runtime-release/current"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void authenticatedRequestStillRequiresTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/knowledge-runtime/runtime-release/current"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void tenantOperatorReadsCurrentRuntimeReleaseAssetsWithoutItemsAlias() throws Exception {
        Instant createdAt = Instant.parse("2026-07-07T00:00:00Z");
        when(service.resolveCurrentRuntimeRelease()).thenReturn(
            new ThirdPartyRuntimeReleaseResponse(
                "v1",
                "runtime-H7",
                "tenant-1",
                "hospital-1",
                7L,
                "baseline-1",
                "a".repeat(64),
                createdAt,
                1,
                List.of(new ClinicalRuntimeReleaseItem(
                    1L,
                    "runtime-H7",
                    "tenant-1",
                    ReleaseSourceLayer.HOSPITAL,
                    VersionedAssetType.TERMINOLOGY,
                    "TERM.LAB.S2S4",
                    ReleaseEntryState.ACTIVE,
                    "term-version-1",
                    "H7",
                    "b".repeat(64),
                    createdAt,
                    "engine-operator",
                    "trace-runtime"))));

        mvc.perform(get("/api/v1/engine/integration/knowledge-runtime/runtime-release/current")
                .with(jwt().jwt(token -> token
                    .subject("engine-operator")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.releaseId").value("runtime-H7"))
            .andExpect(jsonPath("$.data.assetCount").value(1))
            .andExpect(jsonPath("$.data.assets[0].assetType").value("TERMINOLOGY"))
            .andExpect(jsonPath("$.data.assets[0].assetIdentity").value("TERM.LAB.S2S4"))
            .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    @Test
    void anonymousCannotWriteContext() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/knowledge-runtime/context-snapshots"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void everyWriteEndpointRequiresPlatformIdempotencyKey() {
        assertThat(Arrays.stream(ThirdPartyKnowledgeRuntimeController.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PostMapping.class)))
            .allSatisfy(method -> assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null)
                .anyMatch(annotation ->
                    annotation.required()
                        && ("Idempotency-Key".equals(annotation.name())
                            || "Idempotency-Key".equals(annotation.value()))))
                .as(method.getName() + " 必须强制 Idempotency-Key")
                .isTrue());
    }
}
