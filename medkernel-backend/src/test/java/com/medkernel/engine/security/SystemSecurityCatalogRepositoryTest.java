package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统角色与权限目录仓储集成测试。
 *
 * <p>通过 H2 + Flyway 真实读取 V6 种子数据，确保 Spring Data JDBC 能正确映射五维权限枚举。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:security-catalog-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class SystemSecurityCatalogRepositoryTest {

    @Autowired
    SystemRoleRepository roleRepository;

    @Autowired
    SystemPermissionRepository permissionRepository;

    @Test
    void readsSeededBuiltinRolesAndPermissionDimensions() {
        List<SystemRole> roles = roleRepository.findByTenantIdAndActiveFlag("SYSTEM", "Y");
        List<SystemPermission> menuPermissions =
            permissionRepository.findByDimensionAndActiveFlag(PermissionDimension.MENU, "Y");

        assertThat(roles)
            .hasSize(13)
            .extracting(SystemRole::roleCode)
            .contains("platform-admin", "doctor", "implementation-engineer");
        assertThat(roles).allMatch(SystemRole::builtIn);

        assertThat(menuPermissions)
            .extracting(SystemPermission::permissionCode)
            .containsExactlyInAnyOrder(
                "menu.workbench",
                "menu.pilot-setup",
                "menu.clinical-run",
                "menu.quality-improve",
                "menu.compliance-ops",
                "menu.advanced-tools");
        assertThat(menuPermissions).allMatch(permission -> permission.dimension() == PermissionDimension.MENU);
    }
}
