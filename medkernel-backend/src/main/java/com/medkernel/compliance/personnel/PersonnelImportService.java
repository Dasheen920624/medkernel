package com.medkernel.compliance.personnel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.medkernel.compliance.identitybinding.IdentityBindingService;
import com.medkernel.compliance.identitybinding.IdentityProviderType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.ids.Ulid;

/**
 * 人员 CSV 批量预检和幂等提交服务。
 */
@Service
public class PersonnelImportService {

    private static final int MAX_ROWS = 10_000;
    private static final Map<String, AppointmentType> APPOINTMENT_TYPES = Map.of(
        "院内人员", AppointmentType.INTERNAL,
        "集团共享人员", AppointmentType.GROUP_SHARED,
        "外部协作人员", AppointmentType.EXTERNAL_COLLABORATOR,
        "实施人员", AppointmentType.IMPLEMENTATION);
    private static final Map<String, IdentityProviderType> IDENTITY_PROVIDERS = Map.of(
        "开放身份认证（OIDC）", IdentityProviderType.OIDC,
        "统一认证（CAS）", IdentityProviderType.CAS,
        "联盟身份认证（SAML）", IdentityProviderType.SAML,
        "工号", IdentityProviderType.EMPLOYEE_NO,
        "国密数字证书", IdentityProviderType.SM_CA);

    private final PersonnelImportJobRepository jobs;
    private final PersonnelImportRowRepository rows;
    private final PersonRepository people;
    private final PersonAppointmentRepository appointments;
    private final PersonAccountLinkRepository accountLinks;
    private final OrgUnitRepository organizations;
    private final PlatformCredentialRepository credentials;
    private final PersonnelService personnel;
    private final IdentityBindingService identities;
    private final SmCryptoService crypto;
    private final AuditRecorder auditRecorder;

    public PersonnelImportService(
            PersonnelImportJobRepository jobs,
            PersonnelImportRowRepository rows,
            PersonRepository people,
            PersonAppointmentRepository appointments,
            PersonAccountLinkRepository accountLinks,
            OrgUnitRepository organizations,
            PlatformCredentialRepository credentials,
            PersonnelService personnel,
            IdentityBindingService identities,
            SmCryptoService crypto,
            AuditRecorder auditRecorder) {
        this.jobs = jobs;
        this.rows = rows;
        this.people = people;
        this.appointments = appointments;
        this.accountLinks = accountLinks;
        this.organizations = organizations;
        this.credentials = credentials;
        this.personnel = personnel;
        this.identities = identities;
        this.crypto = crypto;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public PersonnelImportResponse preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请选择人员 CSV 文件");
        }
        String tenantId = tenantId();
        byte[] bytes = readBytes(file);
        String digest = "sm3:" + crypto.sm3Hex(new String(bytes, StandardCharsets.UTF_8));
        var existing = jobs.findByTenantIdAndFileDigest(tenantId, digest);
        if (existing.isPresent()) {
            return response(existing.get(), List.of());
        }

        Instant now = Instant.now();
        String jobId = "pij-" + Ulid.newUlid();
        List<PersonnelImportRow> parsed = parse(jobId, tenantId, bytes, now);
        int valid = (int) parsed.stream().filter(row -> "VALID".equals(row.status())).count();
        int conflicts = parsed.size() - valid;
        PersonnelImportJob job = jobs.save(new PersonnelImportJob(
            jobId,
            tenantId,
            safeFileName(file.getOriginalFilename()),
            digest,
            conflicts == 0 ? PersonnelImportStatus.READY : PersonnelImportStatus.HAS_ISSUES,
            parsed.size(),
            valid,
            conflicts,
            0,
            0,
            null,
            1L,
            now,
            actor(),
            now,
            actor(),
            traceId()));
        rows.saveAll(parsed);
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_person_import_job",
            job.jobId(),
            "人员导入预检，总行数=" + job.totalRows() + "，可提交=" + job.validRows());
        return response(job, List.of());
    }

    @Transactional
    public PersonnelImportResponse commit(String jobId, Authentication authentication) {
        String tenantId = tenantId();
        PersonnelImportJob job = jobs.findByTenantIdAndJobId(tenantId, jobId)
            .orElseThrow(() -> ApiException.notFound("人员导入任务 " + jobId));
        if (job.status() == PersonnelImportStatus.COMPLETED
                || job.status() == PersonnelImportStatus.PARTIAL) {
            return response(job, List.of());
        }
        if (job.status() != PersonnelImportStatus.READY) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "导入任务存在阻断问题，处理后才能提交");
        }
        Instant now = Instant.now();
        PersonnelImportJob processing = jobs.save(rewrite(
            job, PersonnelImportStatus.PROCESSING, 0, 0, null, now));
        List<PersonnelDetail.OneTimeActivation> activations = new ArrayList<>();
        int success = 0;
        int failure = 0;
        for (PersonnelImportRow row : rows.findByTenantIdAndJobIdOrderByRowNoAsc(
                tenantId, job.jobId())) {
            try {
                ImportOutcome outcome = applyRow(row, authentication, activations);
                rows.save(rewriteRow(
                    row, "SUCCESS", null, outcome.personId(), outcome.userId()));
                success++;
            } catch (RuntimeException exception) {
                rows.save(rewriteRow(
                    row, "FAILED", compactError(exception.getMessage()), null, null));
                failure++;
            }
        }
        PersonnelImportStatus status = failure == 0
            ? PersonnelImportStatus.COMPLETED
            : PersonnelImportStatus.PARTIAL;
        PersonnelImportJob completed = jobs.save(rewrite(
            processing, status, success, failure, now, Instant.now()));
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_person_import_job",
            completed.jobId(),
            "提交人员导入，成功=" + success + "，失败=" + failure);
        return response(completed, activations);
    }

    @Transactional(readOnly = true)
    public PersonnelImportResponse get(String jobId) {
        PersonnelImportJob job = jobs.findByTenantIdAndJobId(tenantId(), jobId)
            .orElseThrow(() -> ApiException.notFound("人员导入任务 " + jobId));
        return response(job, List.of());
    }

    private List<PersonnelImportRow> parse(
            String jobId,
            String tenantId,
            byte[] bytes,
            Instant now) {
        try (var reader = new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            var records = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get()
                .parse(reader);
            List<PersonnelImportRow> result = new ArrayList<>();
            int rowNo = 1;
            for (CSVRecord record : records) {
                if (rowNo > MAX_ROWS) {
                    throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "单次最多导入 " + MAX_ROWS + " 人");
                }
                result.add(validateRow(jobId, tenantId, rowNo, record, now));
                rowNo++;
            }
            if (result.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "CSV 文件没有人员数据");
            }
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "CSV 解析失败，请使用系统模板并保持 UTF-8 编码");
        }
    }

    private PersonnelImportRow validateRow(
            String jobId,
            String tenantId,
            int rowNo,
            CSVRecord record,
            Instant now) {
        String employeeNo = value(record, "人员编号", "employeeNo");
        String displayName = value(record, "姓名", "displayName");
        String organizationCode = value(record, "机构编码", "organizationCode");
        String departmentCode = value(record, "科室编码", "departmentCode");
        String wardCode = value(record, "病区编码", "wardCode");
        String appointmentType = normalizeAppointmentType(
            value(record, "人员类型", "appointmentType"));
        String positionTitle = value(record, "岗位", "positionTitle");
        String loginName = value(record, "登录名", "loginName");
        String roleCode = normalizeRoleCode(value(record, "角色", "roleCode"));
        String provider = normalizeIdentityProvider(
            value(record, "身份来源", "identityProvider"));
        String externalSubject = value(record, "院内身份标识", "externalSubject");

        List<String> errors = new ArrayList<>();
        requireField(errors, employeeNo, "人员编号");
        requireField(errors, displayName, "姓名");
        requireField(errors, organizationCode, "机构编码");
        requireField(errors, appointmentType, "任职类型");
        OrgUnit organization = organizationCode == null ? null
            : organizations.findByTenantIdAndCode(tenantId, organizationCode).orElse(null);
        if (organization == null) {
            errors.add("机构编码不存在");
        }
        OrgUnit department = departmentCode == null ? null
            : organizations.findByTenantIdAndCode(tenantId, departmentCode).orElse(null);
        if (departmentCode != null && (department == null || department.level() != OrgLevel.DEPARTMENT)) {
            errors.add("科室编码不存在或不是科室");
        }
        if (organization != null && department != null
                && !department.orgPath().startsWith(organization.orgPath() + "/")) {
            errors.add("科室不属于所选机构");
        }
        OrgUnit ward = wardCode == null ? null
            : organizations.findByTenantIdAndCode(tenantId, wardCode).orElse(null);
        if (wardCode != null && (ward == null || ward.level() != OrgLevel.WARD)) {
            errors.add("病区编码不存在或不是病区");
        }
        if (ward != null && department == null) {
            errors.add("填写病区编码前必须填写科室编码");
        }
        if (department != null && ward != null
                && !ward.orgPath().startsWith(department.orgPath() + "/")) {
            errors.add("病区不属于所选科室");
        }
        tryEnum(AppointmentType.class, appointmentType, "任职类型", errors);
        if (roleCode != null
                && RoleCode.fromCode(roleCode).filter(RoleCode::customerAssignable).isEmpty()) {
            errors.add("角色编码无效");
        }
        Person existingPerson = employeeNo == null ? null
            : people.findByTenantIdAndEmployeeNo(tenantId, employeeNo).orElse(null);
        if (loginName != null) {
            credentials.findByTenantIdAndUsername(tenantId, loginName).ifPresent(credential -> {
                boolean ownedByExistingPerson = existingPerson != null
                    && accountLinks.findByTenantIdAndPersonId(
                            tenantId, existingPerson.personId())
                        .map(link -> link.userId().equals(credential.userId()))
                        .orElse(false);
                if (!ownedByExistingPerson) {
                    errors.add("登录名已被其他账号使用");
                }
            });
        }
        if ((provider == null) != (externalSubject == null)) {
            errors.add("身份来源和外部身份必须同时填写");
        }
        tryEnum(IdentityProviderType.class, provider, "身份来源", errors);

        String digest = externalSubject == null ? null : "sm3:" + crypto.sm3Hex(externalSubject);
        String hint = externalSubject == null ? null : subjectHint(externalSubject);
        String action = errors.isEmpty()
            ? (existingPerson == null ? "CREATE" : "UPDATE")
            : "CONFLICT";
        return new PersonnelImportRow(
            "pir-" + Ulid.newUlid(),
            jobId,
            tenantId,
            rowNo,
            employeeNo,
            displayName,
            organizationCode,
            departmentCode,
            wardCode,
            appointmentType,
            positionTitle,
            loginName,
            roleCode,
            provider,
            digest,
            hint,
            action,
            errors.isEmpty() ? "VALID" : "INVALID",
            errors.isEmpty() ? null : String.join("；", errors),
            null,
            null,
            now);
    }

    private ImportOutcome applyRow(
            PersonnelImportRow row,
            Authentication authentication,
            List<PersonnelDetail.OneTimeActivation> activations) {
        String tenantId = tenantId();
        OrgUnit organization = organizations.findByTenantIdAndCode(
                tenantId, row.organizationCode())
            .orElseThrow(() -> ApiException.notFound("机构 " + row.organizationCode()));
        OrgUnit department = row.departmentCode() == null ? null
            : organizations.findByTenantIdAndCode(tenantId, row.departmentCode())
                .orElseThrow(() -> ApiException.notFound("科室 " + row.departmentCode()));
        OrgUnit ward = row.wardCode() == null ? null
            : organizations.findByTenantIdAndCode(tenantId, row.wardCode())
                .orElseThrow(() -> ApiException.notFound("病区 " + row.wardCode()));
        Instant now = Instant.now();
        Person person = people.findByTenantIdAndEmployeeNo(tenantId, row.employeeNo())
            .map(existing -> people.save(new Person(
                existing.personId(),
                existing.tenantId(),
                existing.employeeNo(),
                row.displayName(),
                existing.mobileHint(),
                PersonStatus.ACTIVE,
                existing.version() + 1L,
                existing.createdAt(),
                existing.createdBy(),
                now,
                actor(),
                traceId())))
            .orElseGet(() -> people.save(new Person(
                "person-" + Ulid.newUlid(),
                tenantId,
                row.employeeNo(),
                row.displayName(),
                null,
                PersonStatus.ACTIVE,
                1L,
                now,
                actor(),
                now,
                actor(),
                traceId())));

        var primary = appointments
            .findFirstByTenantIdAndPersonIdAndStatusAndPrimaryFlagOrderByEffectiveFromDesc(
                tenantId, person.personId(), AppointmentStatus.ACTIVE, "Y");
        if (primary.isPresent()) {
            PersonAppointment current = primary.get();
            appointments.save(new PersonAppointment(
                current.appointmentId(),
                current.tenantId(),
                current.personId(),
                organization.id(),
                department == null ? null : department.id(),
                ward == null ? null : ward.id(),
                AppointmentType.valueOf(row.appointmentType().toUpperCase(Locale.ROOT)),
                row.positionTitle(),
                "Y",
                current.effectiveFrom(),
                null,
                AppointmentStatus.ACTIVE,
                current.version() + 1L,
                current.createdAt(),
                current.createdBy(),
                now,
                actor(),
                traceId()));
        } else {
            appointments.save(new PersonAppointment(
                "appt-" + Ulid.newUlid(),
                tenantId,
                person.personId(),
                organization.id(),
                department == null ? null : department.id(),
                ward == null ? null : ward.id(),
                AppointmentType.valueOf(row.appointmentType().toUpperCase(Locale.ROOT)),
                row.positionTitle(),
                "Y",
                now,
                null,
                AppointmentStatus.ACTIVE,
                1L,
                now,
                actor(),
                now,
                actor(),
                traceId()));
        }

        PersonAccountLink link = accountLinks
            .findByTenantIdAndPersonId(tenantId, person.personId())
            .orElse(null);
        boolean accountCreated = false;
        if (link == null && row.loginName() != null) {
            PersonnelService.AccountProvision provision = personnel.createAccount(
                person,
                organization,
                department,
                ward,
                row.loginName(),
                row.roleCode(),
                authentication);
            activations.add(provision.activation());
            link = accountLinks.findByTenantIdAndPersonId(tenantId, person.personId())
                .orElseThrow();
            accountCreated = true;
        }
        if (link != null && !accountCreated && row.roleCode() != null) {
            personnel.assignRole(
                link.userId(), organization, department, ward, row.roleCode(), authentication);
        }
        if (row.identityProvider() != null) {
            if (link == null) {
                throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "绑定身份来源前必须先开通账号");
            }
            identities.createDigest(
                tenantId,
                link.userId(),
                row.identityProvider(),
                row.externalSubjectDigest(),
                row.externalSubjectHint(),
                "人员批量导入绑定身份来源");
        }
        return new ImportOutcome(person.personId(), link == null ? null : link.userId());
    }

    private PersonnelImportResponse response(
            PersonnelImportJob job,
            List<PersonnelDetail.OneTimeActivation> activations) {
        List<PersonnelImportResponse.RowResult> resultRows = rows
            .findByTenantIdAndJobIdOrderByRowNoAsc(job.tenantId(), job.jobId())
            .stream()
            .map(row -> new PersonnelImportResponse.RowResult(
                row.rowNo(),
                row.employeeNo(),
                row.displayName(),
                row.action(),
                row.status(),
                row.errorMessage(),
                row.resultPersonId()))
            .toList();
        return new PersonnelImportResponse(
            job.jobId(),
            job.fileName(),
            job.status(),
            job.totalRows(),
            job.validRows(),
            job.conflictRows(),
            job.successRows(),
            job.failureRows(),
            resultRows,
            List.copyOf(activations));
    }

    private PersonnelImportJob rewrite(
            PersonnelImportJob job,
            PersonnelImportStatus status,
            int successRows,
            int failureRows,
            Instant committedAt,
            Instant now) {
        return new PersonnelImportJob(
            job.jobId(), job.tenantId(), job.fileName(), job.fileDigest(), status,
            job.totalRows(), job.validRows(), job.conflictRows(), successRows, failureRows,
            committedAt, job.version() + 1L, job.createdAt(), job.createdBy(),
            now, actor(), traceId());
    }

    private PersonnelImportRow rewriteRow(
            PersonnelImportRow row,
            String status,
            String message,
            String personId,
            String userId) {
        return new PersonnelImportRow(
            row.rowId(), row.jobId(), row.tenantId(), row.rowNo(), row.employeeNo(),
            row.displayName(), row.organizationCode(), row.departmentCode(),
            row.wardCode(),
            row.appointmentType(), row.positionTitle(), row.loginName(), row.roleCode(),
            row.identityProvider(), row.externalSubjectDigest(), row.externalSubjectHint(),
            row.action(), status, message, personId, userId, row.createdAt());
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "无法读取人员导入文件");
        }
    }

    private String value(CSVRecord record, String... headers) {
        for (String header : headers) {
            if (!record.isMapped(header)) {
                continue;
            }
            String value = record.get(header);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeAppointmentType(String value) {
        if (value == null) {
            return null;
        }
        AppointmentType type = APPOINTMENT_TYPES.get(value);
        return type == null ? value : type.name();
    }

    private String normalizeRoleCode(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(RoleCode.values())
            .filter(RoleCode::customerAssignable)
            .filter(role -> role.displayName().equals(value))
            .map(RoleCode::code)
            .findFirst()
            .orElse(value);
    }

    private String normalizeIdentityProvider(String value) {
        if (value == null) {
            return null;
        }
        IdentityProviderType provider = IDENTITY_PROVIDERS.get(value);
        return provider == null ? value : provider.name();
    }

    private void requireField(List<String> errors, String value, String label) {
        if (value == null) {
            errors.add(label + "不能为空");
        }
    }

    private <T extends Enum<T>> void tryEnum(
            Class<T> type,
            String value,
            String label,
            List<String> errors) {
        if (value == null) {
            return;
        }
        try {
            Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(label + "无效");
        }
    }

    private String subjectHint(String value) {
        int visible = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visible);
    }

    private String safeFileName(String value) {
        return value == null || value.isBlank() ? "人员导入.csv" : value.trim();
    }

    private String compactError(String value) {
        if (value == null || value.isBlank()) {
            return "处理失败";
        }
        String compact = value.trim().replaceAll("\\s+", " ");
        return compact.length() <= 1000 ? compact : compact.substring(0, 1000);
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

    private record ImportOutcome(String personId, String userId) {
    }
}
