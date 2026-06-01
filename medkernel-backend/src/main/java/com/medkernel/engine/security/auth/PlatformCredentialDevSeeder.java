package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

/**
 * 仅 dev profile：为 13 个业务角色和内置超管各准备一个可登录账号（username=角色码，默认密码 Mk@2026dev，须改密）。
 * 幂等：已存在则跳过。生产 profile 不加载本 Bean（无默认口令账号）。
 */
@Component
@Profile("dev")
public class PlatformCredentialDevSeeder implements ApplicationRunner {

    private static final String TENANT = "t-1";
    private static final String DEV_PASSWORD = "Mk@2026dev";
    private static final Map<String, String[]> ACCOUNTS = Map.ofEntries(
        Map.entry("system-superadmin", new String[]{"system-superadmin-1", "system-superadmin"}),
        Map.entry("platform-admin", new String[]{"platform-admin-1", "platform-admin"}),
        Map.entry("group-admin", new String[]{"group-admin-1", "group-admin"}),
        Map.entry("hospital-admin", new String[]{"admin-1", "hospital-admin"}),
        Map.entry("it-ops", new String[]{"it-ops-1", "it-ops"}),
        Map.entry("medical-affairs", new String[]{"medical-affairs-1", "medical-affairs"}),
        Map.entry("qa-manager", new String[]{"qa-manager-1", "qa-manager"}),
        Map.entry("insurance-manager", new String[]{"insurance-manager-1", "insurance-manager"}),
        Map.entry("dept-head", new String[]{"dept-head-1", "dept-head"}),
        Map.entry("implementation-engineer", new String[]{"implementation-1", "implementation-engineer"}),
        Map.entry("specialist", new String[]{"specialist-1", "specialist"}),
        Map.entry("doctor", new String[]{"doctor-1", "doctor"}),
        Map.entry("nurse", new String[]{"nurse-1", "nurse"}),
        Map.entry("audit-compliance", new String[]{"audit-1", "audit-compliance"})
    );

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder encoder;

    public PlatformCredentialDevSeeder(PlatformCredentialRepository credentials,
                                       UserRoleAssignmentRepository roleAssignments,
                                       PasswordEncoder encoder) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        ACCOUNTS.forEach((username, ur) -> {
            String userId = ur[0];
            String roleCode = ur[1];
            if (credentials.findByTenantIdAndUsername(TENANT, username).isEmpty()) {
                credentials.save(new PlatformCredential(null, "cred-" + userId, TENANT, userId, username,
                    encoder.encode(DEV_PASSWORD), "ACTIVE", "Y", null,
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
