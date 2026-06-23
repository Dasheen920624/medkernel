package com.medkernel.compliance.personnel;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.compliance.identitybinding.IdentityBindingRepository;
import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.context.OrgLevel;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 人员、任职、账号和身份来源一体化契约测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonnelControllerTest {

    @Autowired MockMvc mvc;
    @Autowired PersonRepository people;
    @Autowired PersonAppointmentRepository appointments;
    @Autowired PersonAccountLinkRepository accountLinks;
    @Autowired PersonnelImportJobRepository importJobs;
    @Autowired PersonnelImportRowRepository importRows;
    @Autowired IdentityBindingRepository identityBindings;
    @Autowired UserRoleAssignmentRepository roles;
    @Autowired PlatformCredentialRepository credentials;
    @Autowired TenantUserRepository users;
    @Autowired OrgUnitRepository organizations;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        cleanUp();
        Instant now = Instant.now();
        insertOrganization(
            "hospital-a", null, "/tenant-a/hospital-a", "FACILITY",
            "HOSP-A", "示范医院", "HOSPITAL", null);
        insertOrganization(
            "dept-cardio", "hospital-a", "/tenant-a/hospital-a/dept-cardio", "DEPARTMENT",
            "CARDIO", "心内科", null, "cardiology");
        insertOrganization(
            "ward-cardio-1", "dept-cardio",
            "/tenant-a/hospital-a/dept-cardio/ward-cardio-1", "WARD",
            "CARDIO-W1", "心内一病区", null, "cardiology");
    }

    @AfterEach
    void cleanUp() {
        importRows.deleteAll();
        importJobs.deleteAll();
        identityBindings.deleteAll();
        accountLinks.deleteAll();
        appointments.deleteAll();
        roles.deleteAll();
        credentials.deleteAll();
        users.deleteAll();
        people.deleteAll();
        organizations.deleteAll();
    }

    @Test
    void createsPersonAppointmentAndScopedAccountWithoutFreeTextRange() throws Exception {
        mvc.perform(post("/api/v1/compliance/personnel")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo": "EMP-001",
                      "displayName": "王医生",
                      "appointment": {
                        "organizationId": "hospital-a",
                        "departmentId": "dept-cardio",
                        "wardId": "ward-cardio-1",
                        "appointmentType": "INTERNAL",
                        "positionTitle": "主治医师",
                        "primary": true
                      },
                      "account": {
                        "loginName": "wang.doctor",
                        "roleCode": "clinical-user"
                      },
                      "identity": {
                        "providerType": "EMPLOYEE_NO",
                        "externalSubject": "EMP-001"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.person.employeeNo").value("EMP-001"))
            .andExpect(jsonPath("$.data.primaryAppointment.organizationName").value("示范医院"))
            .andExpect(jsonPath("$.data.primaryAppointment.departmentName").value("心内科"))
            .andExpect(jsonPath("$.data.primaryAppointment.wardName").value("心内一病区"))
            .andExpect(jsonPath("$.data.account.username").value("wang.doctor"))
            .andExpect(jsonPath("$.data.identities[0].subjectHint").value("****-001"));

        mvc.perform(get("/api/v1/compliance/personnel")
                .param("page", "1")
                .param("size", "20")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].organizationName").value("示范医院"))
            .andExpect(jsonPath("$.data.items[0].wardName").value("心内一病区"))
            .andExpect(jsonPath("$.data.items[0].accountState").value("RESET_REQUIRED"));
    }

    @Test
    void previewsAndCommitsCsvImportIdempotently() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "院内人员.csv",
            "text/csv",
            ("""
                人员编号,姓名,机构编码,科室编码,病区编码,人员类型,岗位,登录名,角色,身份来源,院内身份标识
                EMP-101,赵医生,HOSP-A,CARDIO,CARDIO-W1,院内人员,住院医师,zhao.doctor,临床使用者,工号,EMP-101
                EMP-102,钱护士,HOSP-A,CARDIO,CARDIO-W1,院内人员,主管护师,qian.nurse,临床使用者,工号,EMP-102
                """).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String preview = mvc.perform(multipart("/api/v1/compliance/personnel/imports:preview")
                .file(file)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.totalRows").value(2))
            .andExpect(jsonPath("$.data.validRows").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String jobId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(preview).path("data").path("jobId").asText();

        mvc.perform(post("/api/v1/compliance/personnel/imports/{jobId}:commit", jobId)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.successRows").value(2))
            .andExpect(jsonPath("$.data.oneTimeActivations", hasSize(2)));

        mvc.perform(post("/api/v1/compliance/personnel/imports/{jobId}:commit", jobId)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.successRows").value(2));

        org.assertj.core.api.Assertions.assertThat(people.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(appointments.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(accountLinks.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(identityBindings.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(roles.count()).isEqualTo(2);
    }

    @Test
    void previewsCsvImportWhenDownloadedTemplateKeepsUtf8Bom() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "院内人员-bom.csv",
            "text/csv",
            ("\uFEFF人员编号,姓名,机构编码,科室编码,病区编码,人员类型,岗位,登录名,角色,身份来源,院内身份标识\n"
                + "EMP-103,吴医生,HOSP-A,CARDIO,CARDIO-W1,院内人员,住院医师,wu.doctor,临床使用者,工号,EMP-103\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/compliance/personnel/imports:preview")
                .file(file)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.validRows").value(1))
            .andExpect(jsonPath("$.data.rows[0].employeeNo").value("EMP-103"));
    }

    @Test
    void platformAdministratorCanMaintainPersonnelDuringOnboarding() throws Exception {
        mvc.perform(get("/api/v1/compliance/personnel")
                .param("page", "1")
                .param("size", "20")
                .with(platformAdministrator()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(post("/api/v1/compliance/personnel")
                .with(platformAdministrator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo": "EMP-104",
                      "displayName": "实施创建医生",
                      "appointment": {
                        "organizationId": "hospital-a",
                        "departmentId": "dept-cardio",
                        "wardId": "ward-cardio-1",
                        "appointmentType": "INTERNAL",
                        "positionTitle": "住院医师",
                        "primary": true
                      },
                      "account": {
                        "loginName": "impl.created",
                        "roleCode": "clinical-user"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.person.employeeNo").value("EMP-104"))
            .andExpect(jsonPath("$.data.account.username").value("impl.created"));
    }

    @Test
    void reimportUpdatesExistingPersonAppointmentAndKeepsOwnedAccountAndIdentityIdempotent()
            throws Exception {
        mvc.perform(post("/api/v1/compliance/personnel")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo": "EMP-201",
                      "displayName": "孙医生",
                      "appointment": {
                        "organizationId": "hospital-a",
                        "departmentId": "dept-cardio",
                        "appointmentType": "INTERNAL",
                        "positionTitle": "住院医师",
                        "primary": true
                      },
                      "account": {
                        "loginName": "sun.doctor",
                        "roleCode": "clinical-user"
                      },
                      "identity": {
                        "providerType": "EMPLOYEE_NO",
                        "externalSubject": "EMP-201"
                      }
                    }
                    """))
            .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "人员更新.csv",
            "text/csv",
            ("""
                人员编号,姓名,机构编码,科室编码,病区编码,人员类型,岗位,登录名,角色,身份来源,院内身份标识
                EMP-201,孙主任,HOSP-A,CARDIO,CARDIO-W1,院内人员,科主任,sun.doctor,临床使用者,工号,EMP-201
                """).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String preview = mvc.perform(multipart("/api/v1/compliance/personnel/imports:preview")
                .file(file)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.rows[0].action").value("UPDATE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String jobId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(preview).path("data").path("jobId").asText();

        mvc.perform(post("/api/v1/compliance/personnel/imports/{jobId}:commit", jobId)
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.successRows").value(1))
            .andExpect(jsonPath("$.data.oneTimeActivations", hasSize(0)));

        mvc.perform(get("/api/v1/compliance/personnel")
                .param("page", "1")
                .param("size", "20")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].displayName").value("孙主任"))
            .andExpect(jsonPath("$.data.items[0].positionTitle").value("科主任"))
            .andExpect(jsonPath("$.data.items[0].identityCount").value(1));

        org.assertj.core.api.Assertions.assertThat(people.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(appointments.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(accountLinks.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(identityBindings.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(roles.count()).isEqualTo(2);
    }

    @Test
    void exportsCustomerFacingChineseImportTemplate() throws Exception {
        mvc.perform(get("/api/v1/compliance/personnel/import-template").with(admin()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString(
                    "人员编号,姓名,机构编码,科室编码,病区编码,人员类型,岗位,登录名,角色,身份来源,院内身份标识")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString(
                    "CARDIO-W1,院内人员,主治医师,wang.doctor,临床使用者,工号")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token
                .subject("platform-admin")
                .claim("tenant_id", "tenant-a"))
            .authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("org.read"),
                new SimpleGrantedAuthority("org.write"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor platformAdministrator() {
        return jwt().jwt(token -> token
                .subject("platform-admin-2")
                .claim("tenant_id", "tenant-a"))
            .authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("org.read"),
                new SimpleGrantedAuthority("org.write"));
    }

    private void insertOrganization(
            String id,
            String parentId,
            String path,
            String level,
            String code,
            String name,
            String facilityType,
            String specialtyId) {
        jdbc.update("""
            INSERT INTO org_unit
                (id, parent_id, tenant_id, org_path, level_code, code, name,
                 facility_type, specialty_id, status, created_at, created_by, updated_at, updated_by)
            VALUES (?, ?, 'tenant-a', ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP,
                    'test', CURRENT_TIMESTAMP, 'test')
            """, id, parentId, path, level, code, name, facilityType, specialtyId);
    }
}
