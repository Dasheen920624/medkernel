package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.bootstrap.MfaPolicyService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 平台级租户开通服务（跨租户）：创建新租户根组织 + 首个医院管理员账号 + 角色。
 *
 * <p>全 profile 注册，由 {@code tenant.write} 高风险权限守卫（实际仅平台管理员具备）。
 * 显式向**新租户**写入数据，不依赖请求方当前租户。
 */
@Service
public class TenantProvisioningService {

    private final OrgUnitRepository orgUnits;
    private final OrgHierarchyRepository orgHierarchy;
    private final PlatformCredentialRepository credentials;
    private final TenantUserRepository users;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialPasswordService credentialPasswords;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final MfaPolicyService mfaPolicyService;
    private final PasswordPolicyService passwordPolicy;

    public TenantProvisioningService(OrgUnitRepository orgUnits,
                                     OrgHierarchyRepository orgHierarchy,
                                     PlatformCredentialRepository credentials,
                                     TenantUserRepository users,
                                     UserRoleAssignmentRepository roleAssignments,
                                     CredentialPasswordService credentialPasswords,
                                     AuditRecorder auditRecorder,
                                     IsolatedAuditPublisher isolatedAudit,
                                     MfaPolicyService mfaPolicyService,
                                     PasswordPolicyService passwordPolicy) {
        this.orgUnits = orgUnits;
        this.orgHierarchy = orgHierarchy;
        this.credentials = credentials;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.credentialPasswords = credentialPasswords;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
        this.mfaPolicyService = mfaPolicyService;
        this.passwordPolicy = passwordPolicy;
    }

    /** 列出客户租户根组织，不暴露唯一平台主租户。 */
    @Transactional(readOnly = true)
    public List<TenantSummary> listTenants() {
        return orgUnits.findAllTenantRoots().stream()
            .filter(unit -> !PlatformTenant.isPlatformTenant(unit.tenantId()))
            .map(o -> new TenantSummary(o.tenantId(), o.name(), o.status().name(), o.createdAt()))
            .toList();
    }

    /** 开通客户服务空间：建根组织 + 首个机构管理员账号（须首登改密）。 */
    @Transactional
    public ProvisionTenantResponse provisionTenant(ProvisionTenantRequest req) {
        String tenantId = req.tenantId().trim();
        String actor = actor();
        mfaPolicyService.assertHighRiskAllowed("platform_tenant", tenantId);
        if (PlatformTenant.isPlatformTenant(tenantId)
                || orgUnits.countByTenantId(tenantId) > 0
                || credentials.findByTenantIdAndUsername(tenantId, req.adminUsername()).isPresent()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE, "platform_tenant", tenantId,
                ErrorCode.ENG_TENANT_001.code(), "开通租户失败：租户已存在或为唯一平台主租户 " + tenantId));
            throw new ApiException(ErrorCode.ENG_TENANT_001,
                "平台主租户为唯一内置租户，不能作为客户租户重复开通");
        }
        Instant now = Instant.now();
        OrgUnit tenantRoot = orgUnits.save(new OrgUnit(
            null, null, tenantId, "/" + tenantId, OrgLevel.TENANT, tenantId, req.tenantName(), null, null, null,
            OrgUnitStatus.ACTIVE, now, actor, now, actor));
        orgHierarchy.insertClosureForNewNode(tenantId, tenantRoot.id(), null);

        String adminUserId = req.adminUsername();
        boolean generated = req.adminInitialPassword() == null || req.adminInitialPassword().isBlank();
        String rawPassword = generated ? passwordPolicy.generateTemporaryPassword() : req.adminInitialPassword();
        passwordPolicy.assertCompliant(rawPassword);
        users.save(new TenantUser(
            null, tenantId, adminUserId, req.adminUsername(), "ACTIVE", 1L,
            now, actor, now, actor, traceId()));
        credentials.save(new PlatformCredential(
            null, "cred-" + tenantId + "-" + adminUserId, tenantId, adminUserId, req.adminUsername(),
            credentialPasswords.encode(rawPassword), "ACTIVE", "Y", null,
            now, actor, now, actor, traceId()));
        roleAssignments.save(new UserRoleAssignment(
            null, tenantId, adminUserId, RoleCode.ORGANIZATION_ADMIN.code(), "TENANT", tenantId, "Y",
            now, actor, now, actor));

        auditRecorder.record(AuditAction.CREATE, "platform_tenant", tenantId,
            "开通租户 " + tenantId + " 首个管理员 " + req.adminUsername());
        return new ProvisionTenantResponse(
            tenantId, adminUserId, req.adminUsername(), generated ? rawPassword : null);
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
