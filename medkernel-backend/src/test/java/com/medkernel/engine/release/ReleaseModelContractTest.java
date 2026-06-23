package com.medkernel.engine.release;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 领域、资产身份和不可变发布清单的数据库模型契约。
 */
class ReleaseModelContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stableAssetIdentityOwnsOnePrimaryDomainAndOptionalRelatedDomains() throws IOException {
        JsonNode schema = schema();
        JsonNode identity = table(schema, "asset_identity");
        JsonNode profile = table(schema, "asset_domain_profile");
        JsonNode related = table(schema, "asset_related_domain");

        assertThat(columnNames(identity))
            .contains(
                "tenant_id", "asset_type", "asset_identity", "status",
                "latest_version_sequence", "created_at", "created_by", "updated_at",
                "updated_by", "trace_id")
            .doesNotContain("version_id", "version_no", "source_layer");
        assertThat(uniqueColumns(identity, "uk_asset_identity_stable"))
            .containsExactly("tenant_id", "asset_type", "asset_identity");
        assertThat(checkExpression(identity, "ck_asset_identity_status"))
            .isEqualTo("status IN('ACTIVE', 'RETIRED')");

        assertThat(columnNames(profile))
            .containsExactlyInAnyOrder(
                "id", "tenant_id", "asset_type", "asset_identity", "primary_domain_code",
                "created_at", "created_by", "updated_at", "updated_by", "trace_id")
            .doesNotContain("version_id", "version_no");
        assertThat(uniqueColumns(profile, "uk_asset_domain_profile_identity"))
            .containsExactly("tenant_id", "asset_type", "asset_identity");

        assertThat(columnNames(related))
            .contains(
                "tenant_id", "asset_type", "asset_identity", "domain_code")
            .doesNotContain("version_id", "version_no");
        assertThat(uniqueColumns(related, "uk_asset_related_domain_identity"))
            .containsExactly("tenant_id", "asset_type", "asset_identity", "domain_code");
    }

    @Test
    void platformAndHospitalReleasesMaterializeExactAssetVersions() throws IOException {
        JsonNode schema = schema();

        assertThat(tableNames(schema)).contains(
            "medical_domain",
            "platform_baseline_release",
            "platform_baseline_item",
            "clinical_runtime_release",
            "clinical_runtime_release_item");

        assertThat(columnNames(table(schema, "platform_baseline_release")))
            .contains(
                "baseline_release_id", "revision_no", "manifest_sha256",
                "published_at", "published_by");
        assertThat(columnNames(table(schema, "platform_baseline_item")))
            .contains(
                "baseline_release_id", "source_tenant_id", "asset_type", "asset_identity",
                "entry_state", "version_id", "version_no", "content_hash");
        assertThat(checkExpression(
            table(schema, "platform_baseline_item"),
            "ck_platform_baseline_item_state"))
            .isEqualTo("entry_state IN('ACTIVE', 'DISABLED')");
        assertThat(columnNames(table(schema, "clinical_runtime_release_item")))
            .contains(
                "release_id", "source_tenant_id", "source_layer", "asset_type",
                "asset_identity", "entry_state", "version_id", "version_no", "content_hash");
        assertThat(checkExpression(
            table(schema, "clinical_runtime_release_item"),
            "ck_clinical_runtime_item_state"))
            .isEqualTo("entry_state IN('ACTIVE', 'DISABLED')");
    }

    @Test
    void domainCatalogUsesStableCodesWithoutVersionSequence() throws IOException {
        JsonNode domain = table(schema(), "medical_domain");

        assertThat(columnNames(domain))
            .contains(
                "domain_code", "name", "parent_domain_code", "status", "sort_order",
                "created_at", "created_by", "updated_at", "updated_by", "trace_id")
            .doesNotContain("version_id", "version_no", "revision_no");
        assertThat(uniqueColumns(domain, "uk_medical_domain_code"))
            .containsExactly("domain_code");
    }

    @Test
    void ruleAndPathwayVersionsOwnMultiTriggerBindings() throws IOException {
        JsonNode trigger = table(schema(), "asset_trigger_binding");

        assertThat(columnNames(trigger))
            .contains(
                "tenant_id", "asset_type", "asset_identity", "version_id",
                "trigger_point", "purpose", "required_fields_json");
        assertThat(uniqueColumns(
            trigger, "uk_asset_trigger_binding_version_trigger"))
            .containsExactly("tenant_id", "version_id", "trigger_point", "purpose");
        assertThat(checkExpression(trigger, "ck_asset_trigger_binding_purpose"))
            .isEqualTo(
                "purpose IN('RULE_EXECUTION', 'PATHWAY_ENTRY_CANDIDATE', 'PATHWAY_PROGRESS')");
        assertThat(checkExpression(trigger, "ck_asset_trigger_binding_type_purpose"))
            .contains(
                "asset_type = 'RULE'",
                "purpose = 'RULE_EXECUTION'",
                "asset_type = 'PATHWAY'");
    }

    private JsonNode schema() throws IOException {
        var resource = getClass().getClassLoader().getResource("db/schema/medkernel.schema.json");
        assertThat(resource).as("数据库规范模型资源").isNotNull();
        return objectMapper.readTree(resource);
    }

    private Set<String> tableNames(JsonNode schema) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode table : schema.path("tables")) {
            names.add(table.path("name").asText());
        }
        return names;
    }

    private JsonNode table(JsonNode schema, String tableName) {
        for (JsonNode table : schema.path("tables")) {
            if (tableName.equals(table.path("name").asText())) {
                return table;
            }
        }
        throw new AssertionError("规范模型缺少表: " + tableName);
    }

    private Set<String> columnNames(JsonNode table) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode column : table.path("columns")) {
            names.add(column.path("name").asText());
        }
        return names;
    }

    private List<String> uniqueColumns(JsonNode table, String constraintName) {
        for (JsonNode constraint : table.path("uniqueConstraints")) {
            if (constraintName.equals(constraint.path("name").asText())) {
                return textList(constraint.path("columns"));
            }
        }
        throw new AssertionError(
            "规范模型缺少唯一约束: " + table.path("name").asText() + "." + constraintName);
    }

    private String checkExpression(JsonNode table, String constraintName) {
        for (JsonNode constraint : table.path("checkConstraints")) {
            if (constraintName.equals(constraint.path("name").asText())) {
                return constraint.path("expression").asText();
            }
        }
        throw new AssertionError(
            "规范模型缺少检查约束: " + table.path("name").asText() + "." + constraintName);
    }

    private List<String> textList(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }
}
