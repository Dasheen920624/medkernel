package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

/**
 * 校验 DISABLE 停用覆盖在真实库（H2）下的持久化与作用域查询：其 {@code override_version_id} 为空仍可
 * 正常落库（五方言 V55 本就声明 NULL），并能被解析期按 (组织生效域 + 适用人群 + 方式) 直查命中
 * （{@link InheritanceResolver} 的 DISABLE 消费路径）；同一生效域唯一约束确保单覆盖、无需 tie-break。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:inheritance-override-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class InheritanceOverrideRepositoryTest {

    private static final String HOSP_PATH = "/TENANT-A/GROUP-A/HOSP-A";
    private static final String DEPT_PATH = "/TENANT-A/GROUP-A/HOSP-A/DEPT-X";

    @Autowired InheritanceOverrideRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void persistsDisableOverrideWithNullOverrideVersionId() {
        InheritanceOverride saved = repository.save(disable("io-disable-1", HOSP_PATH));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.overrideVersionId()).isNull();
        assertThat(saved.overrideMode()).isEqualTo(InheritanceOverrideMode.DISABLE);
        assertThat(saved.propagation()).isEqualTo(InheritancePropagation.INHERITABLE);
    }

    @Test
    void findsDisableOverrideByScopeAndModeIgnoringReplaceAtOtherScope() {
        repository.save(disable("io-disable-1", HOSP_PATH));
        repository.save(replace("io-replace-1", DEPT_PATH, "av-dept-local"));

        List<InheritanceOverride> found = repository
            .findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideMode(
                "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", HOSP_PATH, "adult|inpatient",
                InheritanceOverrideMode.DISABLE);

        assertThat(found).extracting(InheritanceOverride::overrideId).containsExactly("io-disable-1");
    }

    @Test
    void databaseRejectsSecondOverrideInSameEffectiveScope() {
        repository.save(disable("io-disable-1", HOSP_PATH));

        assertThatThrownBy(() -> repository.save(disable("io-disable-2", HOSP_PATH)))
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
    }

    private InheritanceOverride disable(String overrideId, String orgPath) {
        return override(overrideId, InheritanceOverrideMode.DISABLE, null, orgPath);
    }

    private InheritanceOverride replace(String overrideId, String orgPath, String overrideVersionId) {
        return override(overrideId, InheritanceOverrideMode.REPLACE, overrideVersionId, orgPath);
    }

    private InheritanceOverride override(
            String overrideId, InheritanceOverrideMode mode, String overrideVersionId, String orgPath) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new InheritanceOverride(
            null,
            overrideId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-group-inherited",
            overrideVersionId,
            mode,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.PUBLISHED,
            orgPath,
            "adult|inpatient",
            "本机构覆盖差异",
            "本机构覆盖原因",
            "仅 " + orgPath,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04"
        );
    }
}
