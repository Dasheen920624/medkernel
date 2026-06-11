package com.medkernel.engine.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证 OrgUnitController 的 {@code @PreAuthorize("@perm.has('org.read')")} +
 * 类级 {@code @DataScope(requireTenant=true)} 联合行为。
 *
 * <p>测试矩阵：
 * <table>
 *   <tr><th>角色</th><th>有 tenant?</th><th>期望</th></tr>
 *   <tr><td>ROLE_CLINICAL_DECISION_USER（默认含 org.read）</td><td>否</td><td>400 ENG-BASE-001（数据范围拒）→ 证明 @PreAuthorize 通过</td></tr>
 *   <tr><td>ROLE_GUEST（无 org.read）</td><td>否</td><td>403 → 证明 @PreAuthorize 拦截</td></tr>
 *   <tr><td>ROLE_NURSING_COLLABORATOR（默认含 org.read）</td><td>否</td><td>400 ENG-BASE-001</td></tr>
 * </table>
 *
 * <p>使用 dev profile（H2 in-memory + 安全策略 bypass=true），避免 Testcontainers Docker 依赖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrgUnitControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    OrgUnitService orgUnitService;

    @MockBean
    OrgUserDirectoryService orgUserDirectoryService;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLINICAL_DECISION_USER"})
    void doctorWithoutTenantIsRejectedByDataScope() throws Exception {
        when(orgUnitService.listByCurrentTenant(any(PageRequest.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/org/org-units"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/engine/org/org-units"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_NURSING_COLLABORATOR")
    void nurseHasOrgReadButStillNeedsTenant() throws Exception {
        when(orgUnitService.listByCurrentTenant(any(PageRequest.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/org/org-units"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_PLATFORM_GOVERNANCE_ADMIN"})
    void platformAdminAlsoNeedsTenantInRequestContext() throws Exception {
        when(orgUnitService.listByCurrentTenant(any(PageRequest.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/org/org-units"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLINICAL_DECISION_USER"})
    void legacyTenantOrgUnitsRouteIsNotMounted() throws Exception {
        mvc.perform(get("/api/v1/tenant/org-units"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_QUALITY_GOVERNOR"})
    void qualityManagerCanReadUserDirectoryButStillNeedsTenant() throws Exception {
        when(orgUserDirectoryService.list(any(PageRequest.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/org/org-units/users"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadUserDirectory() throws Exception {
        mvc.perform(get("/api/v1/engine/org/org-units/users"))
            .andExpect(status().isForbidden());
    }
}
