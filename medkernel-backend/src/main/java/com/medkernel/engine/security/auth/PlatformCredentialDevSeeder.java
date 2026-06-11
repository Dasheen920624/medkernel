package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 仅 dev profile：为当前职责角色和内置超管各准备一个可登录账号
 * （username=角色码，默认密码 Mk@2026dev，须改密）。
 * 幂等：已存在则跳过。生产 profile 不加载本 Bean（无默认口令账号）。
 */
@Component
@Profile("dev")
public class PlatformCredentialDevSeeder implements ApplicationRunner {

    private static final String TENANT = PlatformTenant.ID;
    private static final String DEV_PASSWORD = "Mk@2026dev";
    private static final Map<String, String[]> ACCOUNTS = Map.ofEntries(
        Map.entry("system-superadmin", new String[]{"system-superadmin-1", "system-superadmin"}),
        Map.entry("platform-governance-admin",
            new String[]{"platform-governance-admin-1", "platform-governance-admin"}),
        Map.entry("platform-knowledge-governor",
            new String[]{"platform-knowledge-governor-1", "platform-knowledge-governor"}),
        Map.entry("organization-admin", new String[]{"organization-admin-1", "organization-admin"}),
        Map.entry("identity-access-admin", new String[]{"identity-access-admin-1", "identity-access-admin"}),
        Map.entry("knowledge-governor", new String[]{"knowledge-governor-1", "knowledge-governor"}),
        Map.entry("clinical-governor", new String[]{"clinical-governor-1", "clinical-governor"}),
        Map.entry("clinical-decision-user",
            new String[]{"clinical-decision-user-1", "clinical-decision-user"}),
        Map.entry("nursing-collaborator",
            new String[]{"nursing-collaborator-1", "nursing-collaborator"}),
        Map.entry("medication-safety-user",
            new String[]{"medication-safety-user-1", "medication-safety-user"}),
        Map.entry("diagnostic-service-user",
            new String[]{"diagnostic-service-user-1", "diagnostic-service-user"}),
        Map.entry("quality-governor", new String[]{"quality-governor-1", "quality-governor"}),
        Map.entry("compliance-auditor", new String[]{"compliance-auditor-1", "compliance-auditor"}),
        Map.entry("integration-operator", new String[]{"integration-operator-1", "integration-operator"}),
        Map.entry("implementation-operator",
            new String[]{"implementation-operator-1", "implementation-operator"})
    );

    private final PlatformCredentialRepository credentials;
    private final TenantUserRepository users;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialPasswordService credentialPasswords;

    public PlatformCredentialDevSeeder(PlatformCredentialRepository credentials,
                                       TenantUserRepository users,
                                       UserRoleAssignmentRepository roleAssignments,
                                       CredentialPasswordService credentialPasswords) {
        this.credentials = credentials;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.credentialPasswords = credentialPasswords;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        ACCOUNTS.forEach((username, ur) -> {
            String userId = ur[0];
            String roleCode = ur[1];
            if (users.findByTenantIdAndUserId(TENANT, userId).isEmpty()) {
                users.save(new TenantUser(
                    null, TENANT, userId, username, "ACTIVE", 1L,
                    now, "dev-seeder", now, "dev-seeder", "seed"));
            }
            if (credentials.findByTenantIdAndUsername(TENANT, username).isEmpty()) {
                credentials.save(new PlatformCredential(null, "cred-" + userId, TENANT, userId, username,
                    credentialPasswords.encode(DEV_PASSWORD), "ACTIVE", "Y", null,
                    now, "dev-seeder", now, "dev-seeder", "seed"));
            }
            boolean hasRole = roleAssignments.findActiveByTenantIdAndUserId(TENANT, userId)
                .stream().anyMatch(a -> roleCode.equals(a.roleCode()));
            if (!hasRole) {
                roleAssignments.save(new UserRoleAssignment(null, TENANT, userId, roleCode,
                    "TENANT", TENANT, "Y", now, "dev-seeder", now, "dev-seeder"));
            }
        });
    }
}
