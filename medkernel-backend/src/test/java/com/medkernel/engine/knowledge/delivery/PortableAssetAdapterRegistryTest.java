package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;

/** 13 类可移植医疗资源统一适配器注册合同测试。 */
class PortableAssetAdapterRegistryTest {

    private static final String DIGEST = "sm3:" + "a".repeat(64);

    private final ObjectMapper json = new ObjectMapper();
    private final PortableAssetAdapterRegistry registry =
        new PortableAssetAdapterRegistry(json, new SmCryptoService());

    @Test
    void registersExactlyAllThirteenTypesAndSupportsExportValidateMaterialize() throws Exception {
        assertThat(registry.adapters())
            .extracting(PortableAssetAdapter::assetType)
            .containsExactly(VersionedAssetType.values());
        assertThat(registry.adapters()).hasSize(13);

        for (VersionedAssetType type : VersionedAssetType.values()) {
            PortableAssetAdapter adapter = registry.require(type);
            PortableAssetFile file = adapter.export(input(type, json.readTree("""
                {"schemaVersion":"1.0","name":"完整正文"}
                """)));

            PortableAssetDocument validated = adapter.validate(file.bytes());
            AtomicReference<PortableAssetDocument> materialized = new AtomicReference<>();
            adapter.materialize(file.bytes(), materialized::set);

            assertThat(file.path()).startsWith("assets/" + type.name() + "/").endsWith(".json");
            assertThat(file.sm3Digest()).startsWith("sm3:").hasSize(68);
            assertThat(validated.assetType()).isEqualTo(type);
            assertThat(validated.assetIdentity()).isEqualTo("ASSET." + type.name());
            assertThat(validated.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.SAFETY_REDLINE);
            assertThat(validated.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.LOCKED);
            assertThat(validated.contentSha256()).hasSize(64);
            assertThat(validated.contentDigest()).startsWith("sm3:").hasSize(68);
            assertThat(materialized.get()).isEqualTo(validated);
        }
    }

    @Test
    void rejectsMissingOrDuplicateAdapterTypesAtRegistryConstruction() {
        List<PortableAssetAdapter> all = registry.adapters();

        assertThatThrownBy(() -> new PortableAssetAdapterRegistry(all.subList(1, all.size())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("缺少")
            .hasMessageContaining(VersionedAssetType.KNOWLEDGE.name());

        List<PortableAssetAdapter> duplicated = new ArrayList<>(all);
        duplicated.add(all.get(0));
        assertThatThrownBy(() -> new PortableAssetAdapterRegistry(duplicated))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("重复")
            .hasMessageContaining(VersionedAssetType.KNOWLEDGE.name());
    }

    @Test
    void canonicalExportIsIndependentOfJsonObjectKeyOrder() throws Exception {
        PortableAssetAdapter adapter = registry.require(VersionedAssetType.RULE);

        PortableAssetFile first = adapter.export(input(VersionedAssetType.RULE, json.readTree("""
            {"schemaVersion":"1.0","name":"规则","dsl":{"then":[],"when":{"a":1,"b":2}}}
            """)));
        PortableAssetFile reordered = adapter.export(input(VersionedAssetType.RULE, json.readTree("""
            {"dsl":{"when":{"b":2,"a":1},"then":[]},"name":"规则","schemaVersion":"1.0"}
            """)));

        assertThat(first.bytes()).containsExactly(reordered.bytes());
        assertThat(first.sm3Digest()).isEqualTo(reordered.sm3Digest());
    }

    @Test
    void rejectsRuleConditionFragmentsPathwaySubpathsAndPathwayCycles() throws Exception {
        assertValidation(() -> registry.require(VersionedAssetType.RULE).export(
            input(VersionedAssetType.RULE, json.readTree("""
                {"schemaVersion":"1.0","conditionFragmentRef":"COND.SHARED"}
                """))));
        assertValidation(() -> registry.require(VersionedAssetType.PATHWAY).export(
            input(VersionedAssetType.PATHWAY, json.readTree("""
                {"schemaVersion":"1.0","subPaths":[{"pathwayCode":"PATH.SHARED"}]}
                """))));
        assertValidation(() -> registry.require(VersionedAssetType.PATHWAY).export(
            input(VersionedAssetType.PATHWAY, json.readTree("""
                {
                  "schemaVersion":"1.0",
                  "nodes":[{"nodeCode":"A"},{"nodeCode":"B"}],
                  "edges":[
                    {"fromNodeCode":"A","toNodeCode":"B"},
                    {"fromNodeCode":"B","toNodeCode":"A"}
                  ]
                }
                """))));
    }

    @Test
    void rejectsTamperedContentAndUnknownRuntimeMetadataOnReadback() throws Exception {
        PortableAssetFile exported = registry.require(VersionedAssetType.KNOWLEDGE).export(
            input(VersionedAssetType.KNOWLEDGE, json.readTree("""
                {"schemaVersion":"1.0","name":"可信正文"}
                """)));
        String canonical = new String(exported.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        String tampered = canonical.replace("可信正文", "篡改正文");
        String withRuntimeHost = canonical.replace(
            "\"licenses\":", "\"hostname\":\"192.0.2.134\",\"licenses\":");

        assertValidation(() -> registry.require(VersionedAssetType.KNOWLEDGE)
            .validate(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertValidation(() -> registry.require(VersionedAssetType.KNOWLEDGE)
            .validate(withRuntimeHost.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsVersionNumberThatRuntimeConsumersCannotResolve() throws Exception {
        PortableAssetDocument.ExportInput valid = input(
            VersionedAssetType.RULE,
            json.readTree("{\"schemaVersion\":\"1.0\",\"name\":\"规则\"}"));
        PortableAssetDocument.ExportInput incompatible = new PortableAssetDocument.ExportInput(
            valid.assetType(),
            valid.assetIdentity(),
            valid.versionId(),
            "1.0.0",
            valid.organizationScope(),
            valid.applicableScope(),
            valid.safetyPolicy(),
            valid.overridePolicy(),
            valid.content(),
            valid.sources(),
            valid.licenses(),
            valid.dependencies(),
            valid.validation(),
            valid.testVectors());

        assertValidation(() -> registry.require(VersionedAssetType.RULE).export(incompatible));
    }

    private PortableAssetDocument.ExportInput input(VersionedAssetType type, JsonNode content) {
        return new PortableAssetDocument.ExportInput(
            type,
            "ASSET." + type.name(),
            "version-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-0001",
            "V1",
            "PLATFORM",
            "ALL",
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED,
            content,
            List.of(new PortableAssetDocument.Source(
                "LICENSED_GUIDELINE", "可交付来源", "2026.1", "chapter-1", DIGEST,
                "license-platform-redistribution")),
            List.of(new PortableAssetDocument.License(
                "license-platform-redistribution", true, "TARGET_HOSPITALS", DIGEST)),
            List.of(new PortableAssetDocument.Dependency(
                VersionedAssetType.FIELD_CATALOG,
                "CONTEXT.CLINICAL.V1",
                "version-field-catalog-0001",
                "V1",
                DIGEST,
                AssetDependencyKind.FIELD)),
            new PortableAssetDocument.Validation(
                "platform-publish-v1", true,
                "version-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-0001", DIGEST),
            List.of(new PortableAssetDocument.TestVector(
                "vector-" + type.name().toLowerCase(java.util.Locale.ROOT),
                json.createObjectNode().put("syntheticCase", "normal"),
                json.createObjectNode().put("accepted", true),
                new PortableAssetDocument.SyntheticProvenance(
                    "synthetic-generator-v1", "1.0.0", "scenario-normal", DIGEST))));
    }

    private void assertValidation(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
