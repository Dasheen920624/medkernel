package com.medkernel.engine.security;

import java.util.List;
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
    void thirteenCustomerRolesResolveEffectiveFiveDimensionProfileAndSecondLevelMenus() throws Exception {
        Set<String> lockedMenuKeys = Set.copyOf(MenuPermissionCatalog.allMenuKeys());

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
            assertThat(data.path("permissions").findValuesAsText("dimension"))
                .as("%s 必须具备五维权限画像", role.code())
                .contains("MENU", "ACTION", "DATA", "ASSET", "ENVIRONMENT");
            assertThat(textValues(data.path("menuKeys")))
                .as("%s 菜单必须来自 27+5 二级/高级菜单目录", role.code())
                .isNotEmpty()
                .contains("workbench")
                .doesNotContain("pilot-setup", "clinical-run", "quality-improve", "compliance-ops",
                    "advanced-tools")
                .allMatch(lockedMenuKeys::contains);
        }
    }

    @Test
    void d0LockedMenuCatalogStillMatchesTwentySevenCustomerMenusAndFiveAdvancedTools() {
        assertThat(MenuPermissionCatalog.allMenus()).hasSize(32);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> "advanced-tools".equals(menu.sectionKey()))
            .hasSize(5);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> !"advanced-tools".equals(menu.sectionKey()))
            .hasSize(27);
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
        return switch (role) {
            case PLATFORM_ADMIN -> "platform-admin-1";
            case GROUP_ADMIN -> "group-admin-1";
            case HOSPITAL_ADMIN -> "admin-1";
            case IT_OPS -> "it-ops-1";
            case MEDICAL_AFFAIRS -> "medical-affairs-1";
            case QA_MANAGER -> "qa-manager-1";
            case INSURANCE_MANAGER -> "insurance-manager-1";
            case DEPT_HEAD -> "dept-head-1";
            case SPECIALIST -> "specialist-1";
            case DOCTOR -> "doctor-1";
            case NURSE -> "nurse-1";
            case MED_TECHNICIAN -> "med-technician-1";
            case PHARMACIST -> "pharmacist-1";
            case AUDIT_COMPLIANCE -> "audit-1";
            case IMPLEMENTATION_ENGINEER -> "implementation-1";
            case SYSTEM_SUPERADMIN -> "system-superadmin-1";
        };
    }
}
