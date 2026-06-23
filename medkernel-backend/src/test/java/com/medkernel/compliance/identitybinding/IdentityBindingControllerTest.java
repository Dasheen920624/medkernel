package com.medkernel.compliance.identitybinding;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityBindingControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityBindingRepository repository;

    @Autowired
    UserRoleAssignmentRepository roleAssignments;

    @Autowired
    TenantUserRepository users;

    @BeforeEach
    void resetBindings() {
        repository.deleteAll();
        roleAssignments.deleteAll();
        users.deleteAll();
    }

    @Test
    void listsBindingsOnlyInsideCurrentTenant() throws Exception {
        mvc.perform(get("/api/v1/compliance/identity-bindings")
                .param("page", "1")
                .param("size", "20")
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void createsBindingWithoutPersistingExternalIdentityPlaintext() throws Exception {
        addMember("doctor-1");

        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("doctor-1"))
            .andExpect(jsonPath("$.data.providerType").value("EMPLOYEE_NO"))
            .andExpect(jsonPath("$.data.subjectHint").value("****-001"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.version").value(1));

        IdentityBinding saved = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();
        org.assertj.core.api.Assertions.assertThat(saved.externalSubjectDigest())
            .startsWith("sm3:")
            .doesNotContain("EMP-001");
    }

    @Test
    void unbindsWithReasonAndOptimisticVersionWithoutDeletingHistory() throws Exception {
        addMember("doctor-1");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());
        IdentityBinding active = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();

        mvc.perform(post("/api/v1/compliance/identity-bindings/{bindingId}:unbind", active.bindingId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "员工离岗，解除统一身份访问",
                      "expectedVersion": 1
                    }
                    """)
                .with(adminJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bindingId").value(active.bindingId()))
            .andExpect(jsonPath("$.data.status").value("UNBOUND"))
            .andExpect(jsonPath("$.data.version").value(2));

        IdentityBinding unbound = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();
        org.assertj.core.api.Assertions.assertThat(unbound.unboundReason())
            .isEqualTo("员工离岗，解除统一身份访问");
        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rebindsPreviouslyUnboundIdentityToAnotherTenantMember() throws Exception {
        addMember("doctor-1");
        addMember("doctor-2");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());
        IdentityBinding active = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();
        mvc.perform(post("/api/v1/compliance/identity-bindings/{bindingId}:unbind", active.bindingId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "原账号离岗",
                      "expectedVersion": 1
                    }
                    """)
                .with(adminJwt()))
            .andExpect(status().isOk());

        mvc.perform(createBinding("doctor-2", "EMP-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bindingId").value(active.bindingId()))
            .andExpect(jsonPath("$.data.userId").value("doctor-2"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.version").value(3));

        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsBindingAnActiveExternalIdentityToAnotherUser() throws Exception {
        addMember("doctor-1");
        addMember("doctor-2");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());

        mvc.perform(createBinding("doctor-2", "EMP-001"))
            .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst().userId())
            .isEqualTo("doctor-1");
    }

    @Test
    void rejectsASecondActiveIdentityFromTheSameProviderForOneUser() throws Exception {
        addMember("doctor-1");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());

        mvc.perform(createBinding("doctor-1", "EMP-002"))
            .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsBindingDisabledTenantMember() throws Exception {
        addMember("doctor-disabled", "DISABLED");

        mvc.perform(createBinding("doctor-disabled", "EMP-009"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("租户成员 doctor-disabled 未启用"));

        org.assertj.core.api.Assertions.assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsUnbindWhenExpectedVersionIsStale() throws Exception {
        addMember("doctor-1");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());
        IdentityBinding active = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();

        mvc.perform(post("/api/v1/compliance/identity-bindings/{bindingId}:unbind", active.bindingId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "管理员使用了过期页面",
                      "expectedVersion": 2
                    }
                    """)
                .with(adminJwt()))
            .andExpect(status().isConflict());

        IdentityBinding unchanged = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();
        org.assertj.core.api.Assertions.assertThat(unchanged.status()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(unchanged.version()).isEqualTo(1L);
    }

    @Test
    void isolatesListAndUnbindByTenant() throws Exception {
        addMember("doctor-1");
        mvc.perform(createBinding("doctor-1", "EMP-001"))
            .andExpect(status().isOk());
        IdentityBinding active = repository.findByTenantIdOrderByUpdatedAtDesc("t-1").getFirst();

        mvc.perform(get("/api/v1/compliance/identity-bindings")
                .with(adminJwt("admin-2", "t-2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());

        mvc.perform(post("/api/v1/compliance/identity-bindings/{bindingId}:unbind", active.bindingId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "跨租户操作必须失败",
                      "expectedVersion": 1
                    }
                    """)
                .with(adminJwt("admin-2", "t-2")))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsWriteWithoutOrganizationManagementPermission() throws Exception {
        addMember("doctor-1");

        mvc.perform(post("/api/v1/compliance/identity-bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "doctor-1",
                      "providerType": "EMPLOYEE_NO",
                      "externalSubject": "EMP-001",
                      "reason": "普通用户不得管理身份"
                    }
                    """)
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(repository.count()).isZero();
    }

    @Test
    void validatesRequiredBindingFields() throws Exception {
        addMember("doctor-1");

        mvc.perform(post("/api/v1/compliance/identity-bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "doctor-1",
                      "providerType": "EMPLOYEE_NO",
                      "externalSubject": " ",
                      "reason": " "
                    }
                    """)
                .with(adminJwt()))
            .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(repository.count()).isZero();
    }

    private void addMember(String userId) {
        addMember(userId, "ACTIVE");
    }

    private void addMember(String userId, String status) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        users.save(new TenantUser(
            null,
            "t-1",
            userId,
            userId,
            status,
            1L,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace-test"));
        roleAssignments.save(new UserRoleAssignment(
            null,
            "t-1",
            userId,
            RoleCode.CLINICAL_USER.code(),
            "TENANT",
            "t-1",
            "Y",
            now,
            "admin-1",
            now,
            "admin-1"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createBinding(
            String userId, String externalSubject) {
        return post("/api/v1/compliance/identity-bindings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": "%s",
                  "providerType": "EMPLOYEE_NO",
                  "externalSubject": "%s",
                  "reason": "账号入职绑定"
                }
                """.formatted(userId, externalSubject))
            .with(adminJwt());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return adminJwt("admin-1", "t-1");
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt(String userId, String tenantId) {
        return jwt().jwt(token -> token
            .subject(userId)
            .claim("tenant_id", tenantId))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
