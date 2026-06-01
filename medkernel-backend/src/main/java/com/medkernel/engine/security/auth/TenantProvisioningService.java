package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.bootstrap.MfaPolicyService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.RequestContext;

/**
 * 平台级租户开通服务（跨租户）：创建新租户根组织 + 首个医院管理员账号 + 角色。
 *
 * <p>仅 dev/test profile；由 {@code tenant.write} 守卫（实际仅平台管理员具备）。
 * 显式向**新租户**写入数据，不依赖请求方当前租户。
 */
@Service
@Profile({"dev", "test"})
public class TenantProvisioningService {

    private final OrgUnitRepository orgUnits;
    private final OrgHierarchyRepository orgHierarchy;
    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialPasswordService credentialPasswords;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final MfaPolicyService mfaPolicyService;
    private final PasswordPolicyService passwordPolicy;

    public TenantProvisioningService(OrgUnitRepository orgUnits,
                                     OrgHierarchyRepository orgHierarchy,
                                     PlatformCredentialRepository credentials,
                                     UserRoleAssignmentRepository roleAssignments,
                                     CredentialPasswordService credentialPasswords,
                                     AuditEventPublisher auditPublisher,
                                     IsolatedAuditPublisher isolatedAudit,
                                     MfaPolicyService mfaPolicyService,
                                     PasswordPolicyService passwordPolicy) {
        this.orgUnits = orgUnits;
        this.orgHierarchy = orgHierarchy;
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.credentialPasswords = credentialPasswords;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
        this.mfaPolicyService = mfaPolicyService;
        this.passwordPolicy = passwordPolicy;
    }

    /** 列出所有租户（根组织），平台视角。 */
    @Transactional(readOnly = true)
    public List<TenantSummary> listTenants() {
        return orgUnits.findAllTenantRoots().stream()
            .map(o -> new TenantSummary(o.tenantId(), o.name(), o.status().name(), o.createdAt()))
            .toList();
    }

    /** 开通新租户：建租户根组织 + 首个医院管理员账号（须首登改密）+ hospital-admin 角色。 */
    @Transactional
    public ProvisionTenantResponse provisionTenant(ProvisionTenantRequest req) {
        String tenantId = req.tenantId();
        String actor = actor();
        mfaPolicyService.assertHighRiskAllowed("platform_tenant", tenantId);
        if (orgUnits.countByTenantId(tenantId) > 0
                || credentials.findByTenantIdAndUsername(tenantId, req.adminUsername()).isPresent()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE, "platform_tenant", tenantId,
                ErrorCode.ENG_TENANT_001.code(), "开通租户失败：租户已存在 " + tenantId));
            throw new ApiException(ErrorCode.ENG_TENANT_001);
        }
        Instant now = Instant.now();
        OrgUnit tenantRoot = orgUnits.save(new OrgUnit(
            null, null, tenantId, "/" + tenantId, OrgLevel.TENANT, tenantId, req.tenantName(), null, null,
            OrgUnitStatus.ACTIVE, now, actor, now, actor));
        orgHierarchy.insertClosureForNewNode(tenantId, tenantRoot.id(), null);

        String adminUserId = req.adminUsername();
        boolean generated = req.adminInitialPassword() == null || req.adminInitialPassword().isBlank();
        String rawPassword = generated ? passwordPolicy.generateTemporaryPassword() : req.adminInitialPassword();
        passwordPolicy.assertCompliant(rawPassword);
        credentials.save(new PlatformCredential(
            null, "cred-" + tenantId + "-" + adminUserId, tenantId, adminUserId, req.adminUsername(),
            credentialPasswords.encode(rawPassword), "ACTIVE", "Y", null,
            now, actor, now, actor, traceId()));
        roleAssignments.save(new UserRoleAssignment(
            null, tenantId, adminUserId, RoleCode.HOSPITAL_ADMIN.code(), "TENANT", tenantId, "Y",
            now, actor, now, actor));

        auditPublisher.publish(AuditAction.CREATE, "platform_tenant", tenantId,
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
