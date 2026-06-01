package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRoleAssignmentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRoleAssignmentRepository userRoleAssignmentRepository;

    @BeforeEach
    void resetSeedAssignments() {
        clearAssignments();
    }

    @AfterEach
    void clearAssignments() {
        userRoleAssignmentRepository.deleteAll();
    }

    @Test
    void canGetAssignmentsUnderTenantContext() throws Exception {
        userRoleAssignmentRepository.save(new UserRoleAssignment(
            null, "t-1", "doctor-1", RoleCode.DOCTOR.code(), "TENANT", "t-1",
            "Y", java.time.Instant.now(), "system", java.time.Instant.now(), "system"
        ));

        mvc.perform(get("/api/v1/compliance/user-roles")
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].userId").value("doctor-1"))
            .andExpect(jsonPath("$.data[0].roleCode").value(RoleCode.DOCTOR.code()));
    }

    @Test
    void canCreateAssignmentSuccessfully() throws Exception {
        var request = new UserRoleAssignmentController.AssignmentCreateRequest(
            "nurse-1",
            RoleCode.NURSE.code(),
            "CAMPUS",
            "c-1"
        );

        mvc.perform(post("/api/v1/compliance/user-roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("nurse-1"))
            .andExpect(jsonPath("$.data.roleCode").value(RoleCode.NURSE.code()))
            .andExpect(jsonPath("$.data.scopeLevel").value("CAMPUS"))
            .andExpect(jsonPath("$.data.scopeCode").value("c-1"));
    }

    @Test
    void cannotCreateSystemSuperAdminAssignmentThroughTenantUserManagement() throws Exception {
        var request = new UserRoleAssignmentController.AssignmentCreateRequest(
            "ops-1",
            "system-superadmin",
            "TENANT",
            "t-1"
        );

        mvc.perform(post("/api/v1/compliance/user-roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    @Test
    void createAssignmentWithInvalidRoleThrowsBadRequest() throws Exception {
        var request = new UserRoleAssignmentController.AssignmentCreateRequest(
            "nurse-1",
            "INVALID_ROLE",
            "TENANT",
            "t-1"
        );

        mvc.perform(post("/api/v1/compliance/user-roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-001"));
    }

    @Test
    void canDeleteAssignmentSuccessfully() throws Exception {
        var saved = userRoleAssignmentRepository.save(new UserRoleAssignment(
            null, "t-1", "doctor-1", RoleCode.DOCTOR.code(), "TENANT", "t-1",
            "Y", java.time.Instant.now(), "system", java.time.Instant.now(), "system"
        ));

        mvc.perform(delete("/api/v1/compliance/user-roles/{id}", saved.id())
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isOk());
    }

    @Test
    void cannotDeleteSystemSuperAdminAssignment() throws Exception {
        var saved = userRoleAssignmentRepository.save(new UserRoleAssignment(
            null, "t-1", "system-superadmin-1", "system-superadmin", "TENANT", "t-1",
            "Y", java.time.Instant.now(), "system", java.time.Instant.now(), "system"
        ));

        mvc.perform(delete("/api/v1/compliance/user-roles/{id}", saved.id())
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    @Test
    void deleteAssignmentFromDifferentTenantThrowsForbidden() throws Exception {
        var saved = userRoleAssignmentRepository.save(new UserRoleAssignment(
            null, "different-tenant", "doctor-1", RoleCode.DOCTOR.code(), "TENANT", "t-1",
            "Y", java.time.Instant.now(), "system", java.time.Instant.now(), "system"
        ));

        mvc.perform(delete("/api/v1/compliance/user-roles/{id}", saved.id())
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isForbidden());
    }
}
