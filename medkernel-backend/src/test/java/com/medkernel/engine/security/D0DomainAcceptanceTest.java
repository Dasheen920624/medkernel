package com.medkernel.engine.security;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class D0DomainAcceptanceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fourteenResponsibilityRolesResolveEffectiveFiveDimensionProfileAndSecondLevelMenus() throws Exception {
        Set<String> lockedMenuKeys = Set.copyOf(MenuPermissionCatalog.allMenuKeys());
        Set<String> observedDimensions = new HashSet<>();

        for (RoleCode role : RoleCode.values()) {
            if (!role.customerAssignable()) {
                continue;
            }

            JsonNode data = readData(mvc.perform(get("/api/v1/security/me")
                    .with(jwt().jwt(token -> token
                        .subject(userId(role))
                        .claim("tenant_id", "t-1")
                        .claim("roles", List.of(role.code())))
                        .authorities(new SimpleGrantedAuthority(role.authority()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

            assertThat(data.path("roles").findValuesAsText("code"))
                .as("%s 登录后必须保留自身角色", role.code())
                .contains(role.code());
            List<String> dimensions = data.path("permissions").findValuesAsText("dimension");
            observedDimensions.addAll(dimensions);
            assertThat(dimensions)
                .as("%s 必须具备菜单、动作、数据和环境权限；无资产职责的角色不得被迫扩权", role.code())
                .contains("MENU", "ACTION", "DATA", "ENVIRONMENT");
            assertThat(textValues(data.path("menuKeys")))
                .as("%s 入口必须来自 32+1+1 导航权限目录", role.code())
                .isNotEmpty()
                .contains("workbench")
                .doesNotContain("pilot-setup", "clinical-run", "quality-improve", "compliance-ops",
                    "advanced-tools")
                .allMatch(lockedMenuKeys::contains);
        }

        assertThat(observedDimensions)
            .as("整套职责角色必须完整覆盖五维权限模型")
            .containsExactlyInAnyOrder("MENU", "ACTION", "DATA", "ASSET", "ENVIRONMENT");
    }

    @Test
    void d0LockedMenuCatalogMatchesEightDomainsAndExplicitPlacements() {
        assertThat(MenuPermissionCatalog.allMenus()).hasSize(34);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .hasSize(32);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.HEADER)
            .hasSize(1);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PROFILE)
            .hasSize(1);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> "sandbox".equals(menu.menuKey()))
            .singleElement()
            .satisfies(menu -> {
                assertThat(menu.sectionKey()).isEqualTo("clinical-collaboration");
                assertThat(menu.placement()).isEqualTo(MenuPermissionCatalog.MenuPlacement.PRIMARY);
            });
    }

    private JsonNode readData(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private List<String> textValues(JsonNode arrayNode) {
        return arrayNode.isArray()
            ? objectMapper.convertValue(
                arrayNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
            : List.of();
    }

    private String userId(RoleCode role) {
        return role.code() + "-1";
    }
}
