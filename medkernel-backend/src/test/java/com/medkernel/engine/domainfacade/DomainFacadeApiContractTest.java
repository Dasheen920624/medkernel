package com.medkernel.engine.domainfacade;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

/**
 * X-DOMAIN 领域门面只读 API 契约测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DomainFacadeApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void list_returnsSeventeenFacadeDefinitionsWithoutClinicalContentSeeds() throws Exception {
        mockMvc.perform(get("/api/v1/engine/domain-facades").with(engineOperatorJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(17))
            .andExpect(jsonPath("$.data[0].code").value("NURSING-01"))
            .andExpect(jsonPath("$.data[*].clinicalContentSeeded", everyItem(is(false))))
            .andExpect(jsonPath("$.data[*].newBusinessEngineRequired", everyItem(is(false))))
            .andExpect(jsonPath("$.data[*].code", hasItem("SVC-DOMAIN-01")))
            .andExpect(jsonPath("$.data[*].code", hasItem("SVC-DOMAIN-02")));
    }

    @Test
    void getServiceCombination_returnsAggregationMembersOnly() throws Exception {
        mockMvc.perform(get("/api/v1/engine/domain-facades/SVC-DOMAIN-01").with(engineOperatorJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("SERVICE_COMBINATION"))
            .andExpect(jsonPath("$.data.engineChain[0]").value("RELEASE"))
            .andExpect(jsonPath("$.data.memberFacadeCodes.length()").value(7))
            .andExpect(jsonPath("$.data.memberFacadeCodes", hasItem("CRITICAL-01")))
            .andExpect(jsonPath("$.data.memberFacadeCodes", hasItem("INFECTION-PH-01")));
    }

    @Test
    void listB0Fixtures_returnsExecutableEvidenceForAllFacadesWithoutModelRequirement() throws Exception {
        mockMvc.perform(get("/api/v1/engine/domain-facades/b0-fixtures").with(engineOperatorJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(17))
            .andExpect(jsonPath("$.data[*].code", hasItem("NURSING-01")))
            .andExpect(jsonPath("$.data[*].status", everyItem(is("PASS"))))
            .andExpect(jsonPath("$.data[*].b0Executable", everyItem(is(true))))
            .andExpect(jsonPath("$.data[*].modelRequired", everyItem(is(false))))
            .andExpect(jsonPath("$.data[*].clinicalContentSeeded", everyItem(is(false))))
            .andExpect(jsonPath("$.data[*].newBusinessEngineRequired", everyItem(is(false))));
    }

    @Test
    void getB0Fixture_declaresSpecialtyExtensionHonestEmptyUntilAssetsExist() throws Exception {
        mockMvc.perform(get("/api/v1/engine/domain-facades/SPECIALTY-EXT-01/b0-fixture")
                .with(engineOperatorJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("SPECIALTY-EXT-01"))
            .andExpect(jsonPath("$.data.status").value("PASS"))
            .andExpect(jsonPath("$.data.honestEmptyWhenAssetsMissing").value(true))
            .andExpect(jsonPath("$.data.assetSeedPolicy").value("NO_SEED_HONEST_EMPTY"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor engineOperatorJwt() {
        return jwt().jwt(token -> token
            .subject("engine-operator")
            .claim("tenant_id", "tenant-1")
            .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }
}
