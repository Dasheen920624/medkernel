package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:pilot-template-repository-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class PilotPackageTemplateRepositoryIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PilotPackageTemplateRepository templates;

    @Autowired
    PilotPackageTemplateItemRepository items;

    @Test
    void activeTemplateAndItemsLoadFromMigratedSchema() {
        seedPilotTemplate("tpl-repository", "tpl-item-rule", "tenant-repository");

        List<PilotPackageTemplate> activeTemplates =
            templates.findByTenantIdAndStatusOrderByTemplateCodeAsc(
                "tenant-repository", PilotPackageTemplateStatus.ACTIVE);
        List<PilotPackageTemplateItem> templateItems =
            items.findByTenantIdAndTemplateIdOrderBySortOrderAsc(
                "tenant-repository", "tpl-repository");

        assertThat(activeTemplates)
            .singleElement()
            .satisfies(template -> {
                assertThat(template.templateId()).isEqualTo("tpl-repository");
                assertThat(template.templateCode()).isEqualTo("TPL.REPOSITORY");
            });
        assertThat(templateItems)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.itemId()).isEqualTo("tpl-item-rule");
                assertThat(item.assetType()).isEqualTo(com.medkernel.engine.versioning.VersionedAssetType.RULE);
                assertThat(item.required()).isTrue();
            });
    }

    private void seedPilotTemplate(String templateId, String itemId, String tenantId) {
        Timestamp now = Timestamp.from(Instant.parse("2026-06-06T08:00:00Z"));
        jdbc.update("""
                INSERT INTO mk_pkg_pilot_package_template (
                    template_id, tenant_id, template_code, name, description,
                    package_code_prefix, default_package_version, status,
                    created_at, created_by, updated_at, updated_by, trace_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            templateId, tenantId, "TPL.REPOSITORY", "仓储映射模板", "验证业务主键映射",
            "PKG.REPOSITORY", "1.0.0", "ACTIVE", now, "tester", now, "tester", "trace-repository");
        jdbc.update("""
                INSERT INTO mk_pkg_pilot_template_item (
                    item_id, tenant_id, template_id, asset_type, asset_id, asset_version,
                    required_flag, sort_order, dependency_note,
                    created_at, created_by, updated_at, updated_by, trace_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            itemId, tenantId, templateId, "RULE", "rule-repository", "1",
            true, 10, "首发规则资产", now, "tester", now, "tester", "trace-repository");
    }
}
