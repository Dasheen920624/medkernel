package com.medkernel.migration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 平台知识权威单一模式源与五方言生成合同。 */
class AuthoritySchemaContractTest {

    private static final List<String> DIALECTS = List.of("h2", "postgres", "oracle", "dm", "kingbase");
    private static final List<String> AUTHORITY_TABLES = List.of(
        "mk_knowledge_authority",
        "mk_knowledge_issuer_instance",
        "mk_knowledge_trust_root",
        "mk_knowledge_signing_key",
        "mk_knowledge_authority_handover",
        "mk_knowledge_key_revocation",
        "mk_knowledge_package_registration"
    );
    private static final Set<String> REQUIRED_AUDIT_COLUMNS = Set.of(
        "tenant_id", "lock_version", "created_at", "created_by",
        "updated_at", "updated_by", "trace_id"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalSchemaDefinesTenantScopedVersionedAndAuditedAuthorityFacts() throws IOException {
        JsonNode schema = schema();
        assertThat(tableNames(schema)).containsAll(AUTHORITY_TABLES);

        for (String tableName : AUTHORITY_TABLES) {
            JsonNode table = table(schema, tableName);
            assertThat(table.path("comment").asText())
                .as(tableName + " 表说明")
                .containsPattern("[\\u3400-\\u9fff]");
            assertThat(columnNames(table))
                .as(tableName + " 租户、版本与审计字段")
                .containsAll(REQUIRED_AUDIT_COLUMNS);
            assertThat(column(table, "tenant_id").path("nullable").asBoolean()).isFalse();
            assertThat(column(table, "lock_version").path("type").asText()).isEqualTo("int64");
            assertThat(column(table, "lock_version").path("nullable").asBoolean()).isFalse();
            assertThat(column(table, "lock_version").path("default").asLong()).isZero();

            for (JsonNode field : table.path("columns")) {
                assertThat(field.path("comment").asText())
                    .as(tableName + "." + field.path("name").asText() + " 中文说明")
                    .containsPattern("[\\u3400-\\u9fff]");
            }

            assertThat(columnNames(table))
                .as(tableName + " 不得耦合宿主或私密密钥材料")
                .noneMatch(AuthoritySchemaContractTest::isForbiddenPersistenceField);
        }
    }

    @Test
    void canonicalSchemaDefinesIdentitySequenceAndLifecycleGuards() throws IOException {
        JsonNode schema = schema();

        assertUniqueColumns(schema, "mk_knowledge_authority",
            List.of("tenant_id"), List.of("tenant_id", "authority_id"));
        assertUniqueColumns(schema, "mk_knowledge_issuer_instance",
            List.of("tenant_id", "authority_id", "issuer_instance_id"));
        assertUniqueColumns(schema, "mk_knowledge_trust_root",
            List.of("tenant_id", "authority_id", "root_fingerprint"));
        assertUniqueColumns(schema, "mk_knowledge_signing_key",
            List.of("tenant_id", "authority_id", "key_id"));
        assertUniqueColumns(schema, "mk_knowledge_authority_handover",
            List.of("tenant_id", "authority_id", "handover_id"),
            List.of("tenant_id", "authority_id", "handover_sequence"));
        assertUniqueColumns(schema, "mk_knowledge_key_revocation",
            List.of("tenant_id", "authority_id", "revocation_id"),
            List.of("tenant_id", "authority_id", "revocation_sequence"),
            List.of("tenant_id", "authority_id", "key_id"));
        assertUniqueColumns(schema, "mk_knowledge_package_registration",
            List.of("tenant_id", "authority_id", "delivery_id"),
            List.of("tenant_id", "authority_id", "release_sequence"),
            List.of("tenant_id", "authority_id", "manifest_digest"));

        assertThat(checkExpression(table(schema, "mk_knowledge_authority"), "ck_mk_knowledge_authority_tenant"))
            .isEqualTo("tenant_id = 't-1'");
        assertThat(checkExpression(table(schema, "mk_knowledge_issuer_instance"),
            "ck_mk_knowledge_issuer_status"))
            .isEqualTo("status IN('STANDBY', 'ACTIVE', 'FROZEN', 'HANDED_OVER', 'REVOKED')");
        assertThat(checkExpression(table(schema, "mk_knowledge_trust_root"),
            "ck_mk_knowledge_trust_root_status"))
            .isEqualTo("status IN('STANDBY', 'ACTIVE', 'RETIRED', 'REVOKED')");
        assertThat(checkExpression(table(schema, "mk_knowledge_signing_key"),
            "ck_mk_knowledge_signing_key_status"))
            .isEqualTo("status IN('STANDBY', 'ACTIVE', 'DISABLED', 'REVOKED')");
        assertThat(checkExpression(table(schema, "mk_knowledge_authority_handover"),
            "ck_mk_knowledge_handover_status"))
            .isEqualTo("status IN('DRAFT', 'FROZEN', 'VERIFIED', 'ACTIVATED', 'ABORTED')");
        assertThat(checkExpression(table(schema, "mk_knowledge_package_registration"),
            "ck_mk_knowledge_delivery_type"))
            .isEqualTo("package_type IN('FULL', 'DELTA')");
        assertThat(checkExpression(table(schema, "mk_knowledge_package_registration"),
            "ck_mk_knowledge_delivery_signing"))
            .isEqualTo("signing_status IN('SIGNED', 'REVOKED')");

        for (String tableName : AUTHORITY_TABLES.subList(1, AUTHORITY_TABLES.size())) {
            JsonNode authorityForeignKey = foreignKey(table(schema, tableName),
                "fk_" + tableName + "_authority");
            assertThat(textList(authorityForeignKey.path("columns")))
                .containsExactly("tenant_id", "authority_id");
            assertThat(authorityForeignKey.path("referencedTable").asText())
                .isEqualTo("mk_knowledge_authority");
            assertThat(textList(authorityForeignKey.path("referencedColumns")))
                .containsExactly("tenant_id", "authority_id");
        }
    }

    @Test
    void generatedFiveDialectBaselinesCarryTheSameAuthorityFactsAndChineseComments()
        throws IOException, URISyntaxException {
        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            for (String tableName : AUTHORITY_TABLES) {
                assertThat(ddl)
                    .as(dialect + "." + tableName)
                    .contains(
                        "CREATE TABLE " + tableName,
                        "COMMENT ON TABLE " + tableName + " IS '",
                        "COMMENT ON COLUMN " + tableName + ".tenant_id IS '",
                        "COMMENT ON COLUMN " + tableName + ".lock_version IS '",
                        "COMMENT ON COLUMN " + tableName + ".trace_id IS '");
            }
        }
    }

    @SafeVarargs
    private void assertUniqueColumns(JsonNode schema, String tableName, List<String>... expected) {
        assertThat(uniqueColumnSets(table(schema, tableName)))
            .as(tableName + " 唯一约束")
            .contains(expected);
    }

    private JsonNode schema() throws IOException {
        var resource = getClass().getClassLoader().getResource("db/schema/medkernel.schema.json");
        assertThat(resource).as("数据库规范模型资源").isNotNull();
        return objectMapper.readTree(resource);
    }

    private String readBaseline(String dialect) throws IOException, URISyntaxException {
        var resource = getClass().getClassLoader()
            .getResource("db/migration/" + dialect + "/V1__baseline.sql");
        assertThat(resource).as(dialect + " 基线资源").isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }

    private JsonNode table(JsonNode schema, String tableName) {
        for (JsonNode candidate : schema.path("tables")) {
            if (tableName.equals(candidate.path("name").asText())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("规范模型缺少表: " + tableName);
    }

    private Set<String> tableNames(JsonNode schema) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode table : schema.path("tables")) {
            names.add(table.path("name").asText());
        }
        return names;
    }

    private Set<String> columnNames(JsonNode table) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode field : table.path("columns")) {
            names.add(field.path("name").asText());
        }
        return names;
    }

    private JsonNode column(JsonNode table, String columnName) {
        for (JsonNode field : table.path("columns")) {
            if (columnName.equals(field.path("name").asText())) {
                return field;
            }
        }
        throw new IllegalArgumentException(
            "规范模型缺少字段: " + table.path("name").asText() + "." + columnName);
    }

    private List<List<String>> uniqueColumnSets(JsonNode table) {
        List<List<String>> result = new ArrayList<>();
        for (JsonNode unique : table.path("uniqueConstraints")) {
            result.add(textList(unique.path("columns")));
        }
        return result;
    }

    private String checkExpression(JsonNode table, String constraintName) {
        for (JsonNode check : table.path("checkConstraints")) {
            if (constraintName.equals(check.path("name").asText())) {
                return check.path("expression").asText();
            }
        }
        throw new IllegalArgumentException(
            "规范模型缺少检查约束: " + table.path("name").asText() + "." + constraintName);
    }

    private JsonNode foreignKey(JsonNode table, String foreignKeyName) {
        for (JsonNode foreignKey : table.path("foreignKeys")) {
            if (foreignKeyName.equals(foreignKey.path("name").asText())) {
                return foreignKey;
            }
        }
        throw new IllegalArgumentException(
            "规范模型缺少外键: " + table.path("name").asText() + "." + foreignKeyName);
    }

    private List<String> textList(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(value.asText());
        }
        return result;
    }

    private static boolean isForbiddenPersistenceField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("private_key")
            || normalized.contains("key_material")
            || normalized.contains("secret")
            || normalized.equals("host")
            || normalized.contains("host_name")
            || normalized.contains("hostname")
            || normalized.contains("ip_address")
            || normalized.contains("deployment_path");
    }
}
