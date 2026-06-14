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

    /**
     * 幂等同步院内人员、主任职、外部身份用户和职责角色。
     *
     * <p>同步用户只建立外部身份主体，不生成平台口令；停用保留人员、任职和身份历史。
     *
     * @return 人员主键
     */
    @Transactional
    public String syncFromExternal(PersonnelSyncCommand command, Authentication authentication) {
        if (command == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "人员同步命令不能为空");
        }
        String tenantId = tenantId();
        String sourceActor = requireExternalSourceActor();
        String employeeNo = required(command.employeeNo(), "人员编号");
        Person current = people.findByTenantIdAndEmployeeNo(tenantId, employeeNo).orElse(null);
        if (current != null && !sourceActor.equals(current.createdBy())) {
            throw ApiException.conflict("人员编号已由人工或其他来源维护，不能被当前来源接管");
        }
        if (command.disable()) {
            if (current == null) {
                throw ApiException.notFound("人员编号 " + employeeNo);
            }
            Instant now = Instant.now();
            people.save(new Person(
                current.personId(), current.tenantId(), current.employeeNo(), current.displayName(),
                current.mobileHint(), PersonStatus.INACTIVE, current.version() + 1L,
                current.createdAt(), current.createdBy(), now, actor(), traceId()));
            appointments.findByTenantIdAndPersonIdOrderByEffectiveFromDesc(tenantId, current.personId())
                .stream()
                .filter(item -> item.status() == AppointmentStatus.ACTIVE)
                .forEach(item -> appointments.save(new PersonAppointment(
                    item.appointmentId(), item.tenantId(), item.personId(), item.organizationId(),
                    item.departmentId(), item.wardId(), item.appointmentType(), item.positionTitle(),
                    item.primaryFlag(), item.effectiveFrom(), now, AppointmentStatus.ENDED,
                    item.version() + 1L, item.createdAt(), item.createdBy(), now, actor(), traceId())));
            accountLinks.findByTenantIdAndPersonId(tenantId, current.personId())
                .ifPresent(link -> {
                    identities.syncExternalIdentity(tenantId, link.userId(), null, null);
                    users.syncExternalRole(link.userId(), null, authentication);
                    users.syncExternalUser(link.userId(), current.displayName(), "DISABLED");
                    accountLinks.save(new PersonAccountLink(
                        link.linkId(), link.tenantId(), link.personId(), link.userId(),
                        "INACTIVE", link.version() + 1L, link.createdAt(), link.createdBy(),
                        now, actor(), traceId()));
                });
            auditRecorder.record(
                AuditAction.UPDATE, "mk_identity_person", current.personId(), "同步停用院内人员");
            return current.personId();
        }

        OrgUnit organization = requireOrganizationByCode(command.organizationCode(), null);
        OrgUnit department = blankToNull(command.departmentCode()) == null
            ? null : requireOrganizationByCode(command.departmentCode(), OrgLevel.DEPARTMENT);
        OrgUnit ward = blankToNull(command.wardCode()) == null
            ? null : requireOrganizationByCode(command.wardCode(), OrgLevel.WARD);
        assertAppointmentHierarchy(organization, department, ward);
        if (command.appointmentType() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "人员同步缺少任职类型");
        }

        Instant now = Instant.now();
        Person person = current == null
            ? people.save(new Person(
                "person-" + Ulid.newUlid(), tenantId, employeeNo,
                required(command.displayName(), "姓名"), null, PersonStatus.ACTIVE, 1L,
                now, actor(), now, actor(), traceId()))
            : people.save(new Person(
                current.personId(), current.tenantId(), current.employeeNo(),
                required(command.displayName(), "姓名"), current.mobileHint(), PersonStatus.ACTIVE,
                current.version() + 1L, current.createdAt(), current.createdBy(),
                now, actor(), traceId()));

        PersonAppointment primary = appointments
            .findFirstByTenantIdAndPersonIdAndStatusAndPrimaryFlagOrderByEffectiveFromDesc(
                tenantId, person.personId(), AppointmentStatus.ACTIVE, "Y")
            .orElse(null);
        if (primary != null && !sourceActor.equals(primary.createdBy())) {
            throw ApiException.conflict("人员主任职已由人工或其他来源维护，不能被当前来源接管");
        }
        appointments.save(new PersonAppointment(
            primary == null ? "appt-" + Ulid.newUlid() : primary.appointmentId(),
            tenantId,
            person.personId(),
            organization.id(),
            department == null ? null : department.id(),
            ward == null ? null : ward.id(),
            command.appointmentType(),
            blankToNull(command.positionTitle()),
            "Y",
            primary == null ? now : primary.effectiveFrom(),
            null,
            AppointmentStatus.ACTIVE,
            primary == null ? 1L : primary.version() + 1L,
            primary == null ? now : primary.createdAt(),
            primary == null ? actor() : primary.createdBy(),
            now,
            actor(),
            traceId()));

        String userId = blankToNull(command.userId());
        if (userId != null) {
            users.syncExternalUser(userId, person.displayName(), "ACTIVE");
            PersonAccountLink link = accountLinks.findByTenantIdAndPersonId(tenantId, person.personId())
                .orElse(null);
            if (link != null && !link.userId().equals(userId)) {
                throw ApiException.conflict("人员已关联其他院内用户标识");
            }
            if (link != null && !sourceActor.equals(link.createdBy())) {
                throw ApiException.conflict("人员账号关联已由人工或其他来源维护，不能被当前来源接管");
            }
            if (link == null) {
                accountLinks.save(new PersonAccountLink(
                    "pal-" + Ulid.newUlid(), tenantId, person.personId(), userId, "ACTIVE", 1L,
                    now, actor(), now, actor(), traceId()));
            }
            syncExternalRole(
                userId, organization, department, ward, command.roleCode(), authentication);
            identities.syncExternalIdentity(
                tenantId,
                userId,
                command.identityProvider(),
                blankToNull(command.identitySubject()));
        }
        auditRecorder.record(
            AuditAction.UPDATE, "mk_identity_person", person.personId(), "同步院内人员与主任职");
        return person.personId();
    }

    @Transactional
    public void disableFromExternal(String internalId, Authentication authentication) {
        Person current = people.findByTenantIdAndPersonId(tenantId(), internalId)
            .orElseThrow(() -> ApiException.notFound("人员 " + internalId));
        syncFromExternal(new PersonnelSyncCommand(
            current.employeeNo(), current.displayName(), null, null, null, null, null,
            null, null, null, null, PersonStatus.INACTIVE, true), authentication);
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

    private void syncExternalRole(
            String userId,
            OrgUnit organization,
            OrgUnit department,
            OrgUnit ward,
            String roleCode,
            Authentication authentication) {
        if (roleCode == null || roleCode.isBlank()) {
            users.syncExternalRole(userId, null, authentication);
            return;
        }
        OrgUnit scope = ward != null ? ward : department == null ? organization : department;
        users.syncExternalRole(
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

    private OrgUnit requireOrganizationByCode(String code, OrgLevel expectedLevel) {
        OrgUnit org = organizations.findByTenantIdAndCode(tenantId(), required(code, "组织编码"))
            .filter(item -> item.status() == OrgUnitStatus.ACTIVE)
            .orElseThrow(() -> ApiException.notFound("组织 code=" + code));
        if (expectedLevel != null && org.level() != expectedLevel) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "组织 " + org.name() + " 不是" + expectedLevel);
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

    private String requireExternalSourceActor() {
        String sourceActor = actor();
        if (!sourceActor.startsWith("integration:")) {
            throw ApiException.forbidden("人员主数据同步必须在受信集成来源上下文中执行");
        }
        return sourceActor;
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
