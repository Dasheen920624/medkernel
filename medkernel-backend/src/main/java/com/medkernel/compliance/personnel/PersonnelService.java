package com.medkernel.compliance.personnel;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.compliance.identitybinding.IdentityBindingCreateRequest;
import com.medkernel.compliance.identitybinding.IdentityBindingResponse;
import com.medkernel.compliance.identitybinding.IdentityBindingService;
import com.medkernel.compliance.user.ComplianceUserCreateRequest;
import com.medkernel.compliance.user.ComplianceUserCreateResponse;
import com.medkernel.compliance.user.ComplianceUserDetail;
import com.medkernel.compliance.user.ComplianceUserRoleRequest;
import com.medkernel.compliance.user.ComplianceUserService;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * 人员、任职、账号和身份来源聚合服务。
 */
@Service
public class PersonnelService {

    private final PersonRepository people;
    private final PersonAppointmentRepository appointments;
    private final PersonAccountLinkRepository accountLinks;
    private final OrgUnitRepository organizations;
    private final ComplianceUserService users;
    private final IdentityBindingService identities;
    private final AuditRecorder auditRecorder;

    public PersonnelService(
            PersonRepository people,
            PersonAppointmentRepository appointments,
            PersonAccountLinkRepository accountLinks,
            OrgUnitRepository organizations,
            ComplianceUserService users,
            IdentityBindingService identities,
            AuditRecorder auditRecorder) {
        this.people = people;
        this.appointments = appointments;
        this.accountLinks = accountLinks;
        this.organizations = organizations;
        this.users = users;
        this.identities = identities;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonnelSummary> page(PageRequest request, String keyword) {
        PageRequest safe = request == null ? PageRequest.defaults() : request;
        String tenantId = tenantId();
        String normalizedKeyword = blankToNull(keyword);
        List<PersonnelSummary> items = people.pageDirectory(
                tenantId, normalizedKeyword, safe.offset(), safe.safeSize())
            .stream()
            .map(this::summary)
            .toList();
        return PageResponse.of(
            items,
            safe,
            people.countDirectory(tenantId, normalizedKeyword));
    }

    @Transactional(readOnly = true)
    public PersonnelDetail detail(String personId) {
        return detail(personId, null);
    }

    @Transactional
    public PersonnelDetail create(PersonCreateRequest request, Authentication authentication) {
        String tenantId = tenantId();
        String employeeNo = required(request.employeeNo(), "人员编号");
        people.findByTenantIdAndEmployeeNo(tenantId, employeeNo).ifPresent(existing -> {
            throw ApiException.conflict("人员编号已存在: " + employeeNo);
        });
        OrgUnit organization = requireOrganization(request.appointment().organizationId(), null);
        OrgUnit department = request.appointment().departmentId() == null
            ? null
            : requireOrganization(request.appointment().departmentId(), OrgLevel.DEPARTMENT);
        OrgUnit ward = request.appointment().wardId() == null
            ? null
            : requireOrganization(request.appointment().wardId(), OrgLevel.WARD);
        assertAppointmentHierarchy(organization, department, ward);

        Instant now = Instant.now();
        String actor = actor();
        Person person = people.save(new Person(
            "person-" + Ulid.newUlid(),
            tenantId,
            employeeNo,
            required(request.displayName(), "姓名"),
            null,
            PersonStatus.ACTIVE,
            1L,
            now,
            actor,
            now,
            actor,
            traceId()));
        appointments.save(new PersonAppointment(
            "appt-" + Ulid.newUlid(),
            tenantId,
            person.personId(),
            organization.id(),
            department == null ? null : department.id(),
            ward == null ? null : ward.id(),
            request.appointment().appointmentType(),
            blankToNull(request.appointment().positionTitle()),
            request.appointment().primary() ? "Y" : "N",
            now,
            null,
            AppointmentStatus.ACTIVE,
            1L,
            now,
            actor,
            now,
            actor,
            traceId()));

        PersonnelDetail.OneTimeActivation activation = null;
        if (request.account() != null) {
            AccountProvision provision = createAccount(
                person,
                organization,
                department,
                ward,
                request.account().loginName(),
                request.account().roleCode(),
                authentication);
            activation = provision.activation();
        }
        if (request.identity() != null) {
            PersonAccountLink link = accountLinks.findByTenantIdAndPersonId(
                    tenantId, person.personId())
                .orElseThrow(() -> new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "绑定身份来源前必须先为人员开通账号"));
            identities.create(tenantId, new IdentityBindingCreateRequest(
                link.userId(),
                request.identity().providerType(),
                request.identity().externalSubject(),
                "人员建档时绑定身份来源"));
        }
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_identity_person",
            person.personId(),
            "创建人员与任职，机构=" + organization.name());
        return detail(person.personId(), activation);
    }

    AccountProvision createAccount(
            Person person,
            OrgUnit organization,
            OrgUnit department,
            OrgUnit ward,
            String loginName,
            String roleCode,
            Authentication authentication) {
        ComplianceUserCreateResponse created = users.create(new ComplianceUserCreateRequest(
            true,
            loginName.trim(),
            person.displayName(),
            loginName.trim(),
            null,
            null), authentication);
        ComplianceUserDetail user = created.user();
        if (roleCode != null && !roleCode.isBlank()) {
            OrgUnit scope = ward != null ? ward : department == null ? organization : department;
            users.assignRole(
                user.userId(),
                new ComplianceUserRoleRequest(
                    roleCode.trim(),
                    roleScopeLevel(scope.level()),
                    scope.id()),
                authentication);
        }
        Instant now = Instant.now();
        accountLinks.save(new PersonAccountLink(
            "pal-" + Ulid.newUlid(),
            tenantId(),
            person.personId(),
            user.userId(),
            "ACTIVE",
            1L,
            now,
            actor(),
            now,
            actor(),
            traceId()));
        return new AccountProvision(
            user,
            new PersonnelDetail.OneTimeActivation(user.username(), created.tempPassword()));
    }

    void assignRole(
            String userId,
            OrgUnit organization,
            OrgUnit department,
            OrgUnit ward,
            String roleCode,
            Authentication authentication) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        OrgUnit scope = ward != null ? ward : department == null ? organization : department;
        users.assignRole(
            userId,
            new ComplianceUserRoleRequest(
                roleCode.trim(),
                roleScopeLevel(scope.level()),
                scope.id()),
            authentication);
    }

    private PersonnelSummary summary(Person person) {
        String tenantId = tenantId();
        PersonAppointment primary = appointments
            .findFirstByTenantIdAndPersonIdAndStatusAndPrimaryFlagOrderByEffectiveFromDesc(
                tenantId, person.personId(), AppointmentStatus.ACTIVE, "Y")
            .orElse(null);
        OrgUnit organization = primary == null ? null
            : organizations.findByTenantIdAndId(tenantId, primary.organizationId()).orElse(null);
        OrgUnit department = primary == null || primary.departmentId() == null ? null
            : organizations.findByTenantIdAndId(tenantId, primary.departmentId()).orElse(null);
        OrgUnit ward = primary == null || primary.wardId() == null ? null
            : organizations.findByTenantIdAndId(tenantId, primary.wardId()).orElse(null);
        PersonAccountLink link = accountLinks
            .findByTenantIdAndPersonId(tenantId, person.personId())
            .orElse(null);
        ComplianceUserDetail user = link == null ? null : users.detail(link.userId());
        int identityCount = link == null ? 0 : identities
            .listForUser(tenantId, link.userId()).size();
        return new PersonnelSummary(
            person.personId(),
            person.employeeNo(),
            person.displayName(),
            person.status(),
            primary == null ? null : primary.appointmentType(),
            organization == null ? null : organization.id(),
            organization == null ? null : organization.name(),
            department == null ? null : department.id(),
            department == null ? null : department.name(),
            ward == null ? null : ward.id(),
            ward == null ? null : ward.name(),
            primary == null ? null : primary.positionTitle(),
            user == null ? null : user.userId(),
            user == null ? null : user.username(),
            accountState(user),
            identityCount);
    }

    private PersonnelDetail detail(
            String personId,
            PersonnelDetail.OneTimeActivation activation) {
        String tenantId = tenantId();
        Person person = people.findByTenantIdAndPersonId(tenantId, personId)
            .orElseThrow(() -> ApiException.notFound("人员 " + personId));
        List<PersonnelDetail.AppointmentView> appointmentViews = appointments
            .findByTenantIdAndPersonIdOrderByEffectiveFromDesc(tenantId, person.personId())
            .stream()
            .map(this::appointmentView)
            .toList();
        PersonnelDetail.AppointmentView primary = appointmentViews.stream()
            .filter(PersonnelDetail.AppointmentView::primary)
            .filter(item -> item.status() == AppointmentStatus.ACTIVE)
            .findFirst()
            .orElse(null);
        PersonAccountLink link = accountLinks
            .findByTenantIdAndPersonId(tenantId, person.personId())
            .orElse(null);
        ComplianceUserDetail user = link == null ? null : users.detail(link.userId());
        PersonnelDetail.AccountView account = user == null ? null
            : new PersonnelDetail.AccountView(
                user.userId(), user.username(), accountState(user));
        List<IdentityBindingResponse> identityViews = link == null
            ? List.of()
            : identities.listForUser(tenantId, link.userId());
        return new PersonnelDetail(
            person, primary, appointmentViews, account, identityViews, activation);
    }

    private PersonnelDetail.AppointmentView appointmentView(PersonAppointment appointment) {
        OrgUnit organization = organizations.findByTenantIdAndId(
                tenantId(), appointment.organizationId())
            .orElse(null);
        OrgUnit department = appointment.departmentId() == null ? null
            : organizations.findByTenantIdAndId(tenantId(), appointment.departmentId())
                .orElse(null);
        OrgUnit ward = appointment.wardId() == null ? null
            : organizations.findByTenantIdAndId(tenantId(), appointment.wardId())
                .orElse(null);
        return new PersonnelDetail.AppointmentView(
            appointment.appointmentId(),
            appointment.organizationId(),
            organization == null ? "机构已停用" : organization.name(),
            appointment.departmentId(),
            department == null ? null : department.name(),
            appointment.wardId(),
            ward == null ? null : ward.name(),
            appointment.appointmentType(),
            appointment.positionTitle(),
            appointment.primary(),
            appointment.status());
    }

    private OrgUnit requireOrganization(String id, OrgLevel expectedLevel) {
        OrgUnit org = organizations.findByTenantIdAndId(tenantId(), required(id, "组织"))
            .filter(item -> item.status() == OrgUnitStatus.ACTIVE)
            .orElseThrow(() -> ApiException.notFound("组织 " + id));
        if (expectedLevel != null && org.level() != expectedLevel) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "组织 " + org.name() + " 不是" + expectedLevel);
        }
        return org;
    }

    private void assertAppointmentHierarchy(
            OrgUnit organization,
            OrgUnit department,
            OrgUnit ward) {
        if (department != null
                && !department.orgPath().startsWith(organization.orgPath() + "/")) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "科室不属于所选医疗机构");
        }
        if (ward != null && department == null) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "选择病区前必须选择所属科室");
        }
        if (ward != null
                && !ward.orgPath().startsWith(department.orgPath() + "/")) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "病区不属于所选科室");
        }
    }

    private String roleScopeLevel(OrgLevel level) {
        return switch (level) {
            case TENANT -> "TENANT";
            case REGION -> "REGION";
            case FACILITY -> "FACILITY";
            case CAMPUS -> "CAMPUS";
            case DEPARTMENT -> "DEPARTMENT";
            case WARD -> "WARD";
            case PLATFORM -> throw new ApiException(
                ErrorCode.VALIDATION_FAILED, "平台治理层不能作为客户角色范围");
        };
    }

    private String accountState(ComplianceUserDetail user) {
        if (user == null) {
            return "NOT_OPENED";
        }
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            return "DISABLED";
        }
        return user.mustChangePwd() ? "RESET_REQUIRED" : "ACTIVE";
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        return RequestContext.currentTraceId();
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record AccountProvision(
        ComplianceUserDetail user,
        PersonnelDetail.OneTimeActivation activation
    ) {
    }
}
