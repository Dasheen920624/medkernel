package com.medkernel.migration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全新项目单一数据库基线静态合同。
 *
 * <p>{@code db/schema/medkernel.schema.json} 是唯一结构真相源，五方言目录只保留生成的
 * {@code V1__baseline.sql}。可启动的数据库另由 Flyway smoke 执行真实语法验证。
 */
class MigrationBaselineContractTest {

    private static final List<String> DIALECTS = List.of("h2", "postgres", "oracle", "dm", "kingbase");
    private static final String BASELINE_FILE = "V1__baseline.sql";
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "(?im)^CREATE TABLE\\s+([a-z][a-z0-9_]*)\\s*\\(");
    private static final Pattern TABLE_COMMENT = Pattern.compile("(?im)^COMMENT ON TABLE\\s+");
    private static final Pattern COLUMN_COMMENT = Pattern.compile("(?im)^COMMENT ON COLUMN\\s+");
    private static final List<String> LEGACY_PRODUCT_TERMS = List.of(
        "运行修订", "运行发布", "发布制品", "运行制品", "运行快照", "清单摘要", "资产清单",
        "平台基线", "权威基线", "运行版本", "冻结基线", "快照运行标识", "运行标识",
        "医院当前运行", "医院运行", "发布包", "运行包", "证据包", "知识包", "配置包");
    private static final String[] LEGACY_PRODUCT_TERM_ARRAY = LEGACY_PRODUCT_TERMS.toArray(String[]::new);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyDialectPublishesOnlyOneGeneratedV1() throws IOException {
        for (String dialect : DIALECTS) {
            try (var files = Files.list(migrationPath(dialect))) {
                assertThat(files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList())
                    .as("%s 只保留全新项目单一基线", dialect)
                    .containsExactly(BASELINE_FILE);
            }

            assertThat(readBaseline(dialect))
                .as("%s 基线必须由统一模型生成", dialect)
                .startsWith("-- MedKernel 全新上线数据库基线")
                .contains("scripts/db/generate-migrations.mjs")
                .contains("db/schema/medkernel.schema.json");
        }
    }

    @Test
    void generatedBaselinesMatchCanonicalTablesAndChineseComments() throws IOException {
        JsonNode schema = schema();
        Set<String> canonicalTables = new LinkedHashSet<>();
        int columnCount = 0;
        for (JsonNode table : schema.path("tables")) {
            canonicalTables.add(table.path("name").asText());
            columnCount += table.path("columns").size();
        }

        assertThat(schema.path("version").asInt()).isEqualTo(1);
        assertThat(canonicalTables).as("规范模型表名不得重复")
            .hasSize(schema.path("tables").size());

        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            assertThat(names(CREATE_TABLE, ddl)).as("%s 终态表集合", dialect)
                .containsExactlyElementsOf(canonicalTables);
            assertThat(count(TABLE_COMMENT, ddl)).as("%s 每张表都有中文注释", dialect)
                .isEqualTo(canonicalTables.size());
            assertThat(count(COLUMN_COMMENT, ddl)).as("%s 每个字段都有中文注释", dialect)
                .isEqualTo(columnCount);
        }
    }

    @Test
    void generatedDatabaseCommentsUseCustomerUnderstandableReleaseTerms() throws IOException {
        assertThat(objectMapper.writeValueAsString(schema()))
            .as("数据库模式注释不得继续使用旧发布容器或运行修订口径")
            .doesNotContain(LEGACY_PRODUCT_TERM_ARRAY);
        for (String dialect : DIALECTS) {
            assertThat(readBaseline(dialect))
                .as("%s 数据库注释不得继续使用旧发布容器或运行修订口径", dialect)
                .doesNotContain(LEGACY_PRODUCT_TERM_ARRAY);
        }
    }

    @Test
    void canonicalModelRemovesLegacyRoleAndExpertSignoffBaggage() throws IOException {
        JsonNode schema = schema();
        Set<String> tableNames = new LinkedHashSet<>();
        for (JsonNode table : schema.path("tables")) {
            tableNames.add(table.path("name").asText());
        }
        assertThat(tableNames).doesNotContain(
            "medkernel_meta", "sys_role", "sys_permission", "role_permission", "rule_signoff",
            "mk_knowledge_source_version_approval", "mk_engine_condition_fragment");

        JsonNode evalRun = table(schema, "mk_llm_eval_run");
        assertThat(columnNames(evalRun)).doesNotContain("reviewer", "signed_at", "review_comment");

        String serialized = objectMapper.writeValueAsString(schema).toLowerCase(Locale.ROOT);
        assertThat(serialized).doesNotContain(
            "独立专家", "双签", "rule_signoff", "sys_role", "sys_permission", "role_permission",
            "mk_knowledge_source_version_approval", "mk_engine_condition_fragment",
            "condition_fragment", "条件片段", "subpathway", "subpathwayref", "子路径",
            "parent_template_id", "idx_pathway_template_parent", "p6");

        for (String dialect : DIALECTS) {
            assertThat(readBaseline(dialect).toLowerCase(Locale.ROOT))
                .as("%s 不生成旧门阀", dialect)
                .doesNotContain(
                    "独立专家", "双签", "rule_signoff", "sys_role", "sys_permission", "role_permission",
                    "mk_knowledge_source_version_approval", "condition_fragment", "条件片段",
                    "subpathway", "subpathwayref", "子路径",
                    "parent_template_id", "idx_pathway_template_parent", "p6");
        }
    }

    @Test
    void baselinePreservesCompleteMedicalEngineAndSaasSurfaces() throws IOException {
        Set<String> requiredTables = Set.of(
            "audit_event",
            "org_unit",
            "tenant_user",
            "user_role_assignment",
            "knowledge_identity",
            "knowledge_asset_version",
            "platform_baseline_release",
            "platform_baseline_item",
            "clinical_runtime_release",
            "clinical_runtime_release_item",
            "rule_definition",
            "rule_version",
            "evaluation_run",
            "followup_plan",
            "integration_adapter",
            "mk_llm_provider",
            "mk_llm_eval_run",
            "mk_llm_model_version_bundle",
            "mk_knowledge_production_job",
            "mk_knowledge_production_candidate",
            "mk_knowledge_initialization_batch",
            "mk_knowledge_initialization_item",
            "mk_sandbox_run"
        );

        Set<String> canonicalTables = new LinkedHashSet<>();
        for (JsonNode table : schema().path("tables")) {
            canonicalTables.add(table.path("name").asText());
        }
        assertThat(canonicalTables).containsAll(requiredTables);

        for (String dialect : DIALECTS) {
            assertThat(names(CREATE_TABLE, readBaseline(dialect)))
                .as("%s 完整功能表面不得因权限收缩被删除", dialect)
                .containsAll(requiredTables);
        }
    }

    @Test
    void clinicalRuntimeTablesPersistOnlyTheHospitalRuntimeReleaseReference() throws IOException {
        for (String tableName : List.of("clinical_event", "context_snapshot", "recommendation_trigger")) {
            assertThat(columnNames(table(schema(), tableName)))
                .as("%s 临床运行事实只绑定服务端锁定的机构生效版本", tableName)
                .contains("runtime_release_id")
                .doesNotContain("package_id", "package_code", "package_version");
        }
        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            for (String tableName : List.of("clinical_event", "context_snapshot", "recommendation_trigger")) {
                assertThat(createTableBlock(ddl, tableName))
                    .as("%s.%s 临床运行事实不得回引旧包三元组", dialect, tableName)
                    .contains("runtime_release_id VARCHAR")
                    .doesNotContain("package_id", "package_code", "package_version");
            }
        }
    }

    @Test
    void clinicalRuntimeEvidenceReferencesTheExactHospitalReleaseLedger() throws IOException {
        for (String tableName : List.of("clinical_event", "context_snapshot", "recommendation_trigger")) {
            String foreignKeyName = "fk_" + tableName + "_runtime_release";
            JsonNode foreignKey = foreignKey(table(schema(), tableName), foreignKeyName);
            assertThat(textList(foreignKey.path("columns")))
                .as("%s 必须通过机构生效版本 ID 固化实际生效组合", tableName)
                .containsExactly("runtime_release_id");
            assertThat(foreignKey.path("referencedTable").asText())
                .isEqualTo("clinical_runtime_release");
            assertThat(textList(foreignKey.path("referencedColumns")))
                .containsExactly("release_id");
        }

        for (String dialect : DIALECTS) {
            assertThat(readBaseline(dialect))
                .as("%s 临床运行证据必须引用机构生效版本账本", dialect)
                .contains(
                    "ALTER TABLE clinical_event ADD CONSTRAINT fk_clinical_event_runtime_release "
                        + "FOREIGN KEY (runtime_release_id) REFERENCES clinical_runtime_release (release_id);",
                    "ALTER TABLE context_snapshot ADD CONSTRAINT fk_context_snapshot_runtime_release "
                        + "FOREIGN KEY (runtime_release_id) REFERENCES clinical_runtime_release (release_id);",
                    "ALTER TABLE recommendation_trigger ADD CONSTRAINT fk_recommendation_trigger_runtime_release "
                        + "FOREIGN KEY (runtime_release_id) REFERENCES clinical_runtime_release (release_id);"
                );
        }
    }

    @Test
    void retiredPackageContainerTablesAreRemovedFromTheCleanV1() throws IOException {
        Set<String> retiredTables = Set.of(
            "knowledge_package",
            "institution_extension_package",
            "package_item",
            "release_plan",
            "sync_log",
            "mk_aik_pack_job",
            "mk_pkg_package_entitlement",
            "mk_pkg_pilot_package_template",
            "mk_pkg_pilot_template_item",
            "mk_pkg_tenant_package_reference"
        );

        Set<String> tableNames = new LinkedHashSet<>();
        for (JsonNode candidate : schema().path("tables")) {
            tableNames.add(candidate.path("name").asText());
        }
        assertThat(tableNames)
            .as("旧发布容器、构包作业、包授权和包同步表不得进入全新 V1")
            .doesNotContainAnyElementsOf(retiredTables);

        for (String dialect : DIALECTS) {
            assertThat(names(CREATE_TABLE, readBaseline(dialect)))
                .as("%s 旧发布容器表不得生成", dialect)
                .doesNotContainAnyElementsOf(retiredTables);
        }
    }

    @Test
    void hospitalRuntimeUsesOneAppendOnlyReleaseLedgerWithoutMutableBindingTable() throws IOException {
        Set<String> tableNames = new LinkedHashSet<>();
        for (JsonNode candidate : schema().path("tables")) {
            tableNames.add(candidate.path("name").asText());
        }
        assertThat(tableNames)
            .contains("clinical_runtime_release")
            .doesNotContain("clinical_runtime_package_binding");

        assertThat(columnNames(table(schema(), "clinical_runtime_release")))
            .contains(
                "release_id", "tenant_id", "hospital_id", "revision_no",
                "platform_baseline_release_id", "manifest_sha256", "rollback_from_release_id",
                "activated_at", "activated_by"
            )
            .doesNotContain(
                "authority_package_id", "authority_package_code", "authority_package_version",
                "group_extension_package_id", "hospital_extension_package_id",
                "effective_content_sha256");
        assertThat(columnNames(table(schema(), "clinical_runtime_release_item")))
            .contains(
                "release_id", "source_tenant_id", "source_layer", "asset_type",
                "asset_identity", "entry_state", "version_id", "version_no", "content_hash");
        for (String dialect : DIALECTS) {
            assertThat(readBaseline(dialect))
                .as("%s 机构生效版本账本", dialect)
                .contains(
                    "CREATE TABLE clinical_runtime_release",
                    "CREATE TABLE clinical_runtime_release_item",
                    "uk_clinical_runtime_release_revision",
                    "platform_baseline_release_id VARCHAR",
                    "manifest_sha256 VARCHAR",
                    "source_tenant_id VARCHAR",
                    "source_layer VARCHAR"
                )
                .doesNotContain("CREATE TABLE clinical_runtime_package_binding");
        }
    }

    @Test
    void versionedContentLifecycleUsesOnlyDraftPublishedAndWithdrawn() throws IOException {
        JsonNode schema = schema();
        JsonNode assetVersion = table(schema, "mk_version_asset_version");
        assertThat(checkExpression(assetVersion, "ck_mk_version_asset_version_status"))
            .as("内容版本生命周期不得混入审核态、批准态或退役态")
            .isEqualTo("status IN('DRAFT', 'PUBLISHED', 'WITHDRAWN')");
        assertThat(columnComment(assetVersion, "status"))
            .as("内容版本状态注释必须跟三态模型一致")
            .contains("DRAFT 草稿", "PUBLISHED 已发布", "WITHDRAWN 已撤回")
            .doesNotContain("IN_REVIEW", "APPROVED", "DEPRECATED", "RETIRED");

        JsonNode replayAsset = table(schema, "mk_sandbox_replay_asset_binding");
        assertThat(checkExpression(replayAsset, "ck_mk_sandbox_replay_asset_status"))
            .as("沙箱历史回放只能绑定曾发布或撤回的内容版本")
            .isEqualTo("historical_status IN('PUBLISHED', 'WITHDRAWN')");

        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            assertThat(ddl)
                .as("%s 内容版本生命周期三态", dialect)
                .contains(
                    "ck_mk_version_asset_version_status CHECK (status IN('DRAFT', 'PUBLISHED', 'WITHDRAWN'))",
                    "ck_mk_sandbox_replay_asset_status CHECK (historical_status IN('PUBLISHED', 'WITHDRAWN'))",
                    "COMMENT ON COLUMN mk_version_asset_version.status IS '统一内容版本状态：DRAFT 草稿、PUBLISHED 已发布、WITHDRAWN 已撤回'")
                .doesNotContain(
                    "ck_mk_version_asset_version_status CHECK (status IN('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'DEPRECATED', 'RETIRED'))",
                    "ck_mk_sandbox_replay_asset_status CHECK (historical_status IN('PUBLISHED', 'DEPRECATED', 'RETIRED'))",
                    "统一生命周期：DRAFT 草稿、IN_REVIEW 评审中、APPROVED 已批准");
        }
    }

    @Test
    void inheritanceOverrideLifecycleUsesOnlyActiveAndRetiredCoverageFacts() throws IOException {
        JsonNode override = table(schema(), "mk_version_inheritance_override");
        assertThat(checkExpression(override, "ck_mk_version_inheritance_override_lifecycle"))
            .as("机构覆盖只表达启用或退役事实，不再保留额外审核态")
            .isEqualTo("lifecycle_status IN('ACTIVE', 'RETIRED')");
        assertThat(columnComment(override, "lifecycle_status"))
            .contains("ACTIVE 已启用", "RETIRED 已退役")
            .doesNotContain("DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED 已发布", "DEPRECATED");

        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            assertThat(ddl)
                .as("%s 机构覆盖生命周期必须是最小启用/退役事实", dialect)
                .contains(
                    "ck_mk_version_inheritance_override_lifecycle CHECK (lifecycle_status IN('ACTIVE', 'RETIRED'))",
                    "COMMENT ON COLUMN mk_version_inheritance_override.lifecycle_status IS '覆盖状态：ACTIVE 已启用 / RETIRED 已退役；ACTIVE 参与解析，RETIRED 仅用于历史重放窗口'")
                .doesNotContain(
                    "ck_mk_version_inheritance_override_lifecycle CHECK (lifecycle_status IN('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'DEPRECATED', 'RETIRED'))",
                    "覆盖生命周期：DRAFT 草稿 / IN_REVIEW 待评审 / APPROVED 已通过");
        }
    }

    @Test
    void interopEvidenceSourcesUseEvidenceExportNamingInsteadOfEvidencePackage() throws IOException {
        String serialized = objectMapper.writeValueAsString(schema());
        assertThat(serialized)
            .as("证据来源类型不得继续暴露旧证据导出口径")
            .doesNotContain("EMR_LEVEL_EVIDENCE_PACKAGE")
            .contains("EMR_LEVEL_EVIDENCE_EXPORT");
        for (String dialect : DIALECTS) {
            assertThat(readBaseline(dialect))
                .as("%s 证据来源注释不得继续使用旧证据导出口径", dialect)
                .doesNotContain("EMR_LEVEL_EVIDENCE_PACKAGE")
                .contains("EMR_LEVEL_EVIDENCE_EXPORT");
        }
    }

    @Test
    void generatedBaselinesContainOnlyStructureAndCriticalDatabaseGuards() throws IOException {
        for (String dialect : DIALECTS) {
            String ddl = readBaseline(dialect);
            assertThat(ddl).as("%s 静态目录由应用播种，V1 只负责结构", dialect)
                .doesNotContain("\nINSERT ", "\nUPDATE ", "\nDELETE ")
                .contains(
                    "uk_mk_llm_model_version_active_scope",
                    "ck_mk_llm_model_version_active_scope",
                    "uk_mk_llm_provider_credential_tenant",
                    "idx_mk_llm_provider_credential_tenant",
                    "uk_mk_knowledge_init_batch_code",
                    "uk_mk_knowledge_init_item_candidate");
        }
    }

    @Test
    void canonicalModelDoesNotDuplicatePrimaryOrUniqueIndexes() throws IOException {
        for (JsonNode table : schema().path("tables")) {
            Set<List<String>> keyColumns = new LinkedHashSet<>();
            if (!table.path("primaryKey").isMissingNode() && !table.path("primaryKey").isNull()) {
                keyColumns.add(textList(table.path("primaryKey").path("columns")));
            }
            for (JsonNode unique : table.path("uniqueConstraints")) {
                keyColumns.add(textList(unique.path("columns")));
            }
            for (JsonNode index : table.path("indexes")) {
                List<String> indexColumns = new java.util.ArrayList<>();
                for (JsonNode column : index.path("columns")) {
                    indexColumns.add(column.path("name").asText());
                }
                assertThat(keyColumns)
                    .as("%s.%s 不得重复主键或唯一约束索引", table.path("name").asText(), index.path("name").asText())
                    .doesNotContain(indexColumns);
            }
        }
    }

    @Test
    void largeEncryptedAndStructuredPayloadsUseDialectAppropriateTypes() throws IOException {
        assertThat(readBaseline("oracle"))
            .contains("credential_ciphertext CLOB NOT NULL")
            .doesNotContain("credential_ciphertext VARCHAR2");
        assertThat(readBaseline("dm"))
            .contains("credential_ciphertext VARCHAR2(4096) NOT NULL");
        for (String dialect : List.of("h2", "postgres", "kingbase")) {
            assertThat(readBaseline(dialect))
                .as("%s 模型凭据密文不得受短字符串上限影响", dialect)
                .contains("credential_ciphertext VARCHAR(4096) NOT NULL");
        }
    }

    private JsonNode schema() throws IOException {
        var resource = getClass().getClassLoader().getResource("db/schema/medkernel.schema.json");
        assertThat(resource).as("数据库规范模型资源").isNotNull();
        return objectMapper.readTree(resource);
    }

    private JsonNode table(JsonNode schema, String tableName) {
        for (JsonNode table : schema.path("tables")) {
            if (tableName.equals(table.path("name").asText())) {
                return table;
            }
        }
        throw new IllegalArgumentException("规范模型缺少表: " + tableName);
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

    private Set<String> columnNames(JsonNode table) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode column : table.path("columns")) {
            names.add(column.path("name").asText());
        }
        return names;
    }

    private String columnComment(JsonNode table, String columnName) {
        for (JsonNode column : table.path("columns")) {
            if (columnName.equals(column.path("name").asText())) {
                return column.path("comment").asText();
            }
        }
        throw new IllegalArgumentException(
            "规范模型缺少字段: " + table.path("name").asText() + "." + columnName);
    }

    private String checkExpression(JsonNode table, String constraintName) {
        for (JsonNode constraint : table.path("checkConstraints")) {
            if (constraintName.equals(constraint.path("name").asText())) {
                return constraint.path("expression").asText();
            }
        }
        throw new IllegalArgumentException(
            "规范模型缺少检查约束: " + table.path("name").asText() + "." + constraintName);
    }

    private List<String> textList(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }

    private Set<String> names(Pattern pattern, String value) {
        Set<String> names = new LinkedHashSet<>();
        var matcher = pattern.matcher(value);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private long count(Pattern pattern, String value) {
        return pattern.matcher(value).results().count();
    }

    private String createTableBlock(String ddl, String tableName) {
        Pattern tablePattern = Pattern.compile(
            "(?ims)^CREATE TABLE\\s+" + tableName + "\\s*\\(.*?^\\);");
        var matcher = tablePattern.matcher(ddl);
        assertThat(matcher.find()).as("生成基线缺少表: %s", tableName).isTrue();
        return matcher.group();
    }

    private String readBaseline(String dialect) throws IOException {
        return Files.readString(migrationPath(dialect).resolve(BASELINE_FILE));
    }

    private Path migrationPath(String dialect) {
        var resource = getClass().getClassLoader().getResource("db/migration/" + dialect);
        assertThat(resource).as("%s 迁移资源目录", dialect).isNotNull();
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("无法读取迁移资源目录: " + dialect, exception);
        }
    }
}
