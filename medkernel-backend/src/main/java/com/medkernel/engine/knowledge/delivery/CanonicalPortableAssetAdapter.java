package com.medkernel.engine.knowledge.delivery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetSelfContainmentPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;

/** 基于统一稳定资产文档的单类型确定性适配器。 */
final class CanonicalPortableAssetAdapter implements PortableAssetAdapter {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern DIGEST = Pattern.compile("(?:sm3|sha256):[0-9a-f]{64}");

    private static final Comparator<PortableAssetDocument.Dependency> DEPENDENCY_ORDER =
        Comparator.comparing((PortableAssetDocument.Dependency dependency) -> dependency.assetType().name())
            .thenComparing(PortableAssetDocument.Dependency::assetIdentity)
            .thenComparing(PortableAssetDocument.Dependency::versionId);
    private static final Comparator<PortableAssetDocument.TestVector> TEST_ORDER =
        Comparator.comparing(PortableAssetDocument.TestVector::vectorId);
    private static final Comparator<PortableAssetDocument.Source> SOURCE_ORDER =
        Comparator.comparing(PortableAssetDocument.Source::sourceType)
            .thenComparing(PortableAssetDocument.Source::title)
            .thenComparing(PortableAssetDocument.Source::sourceVersion)
            .thenComparing(PortableAssetDocument.Source::citationAnchor);
    private static final Comparator<PortableAssetDocument.License> LICENSE_ORDER =
        Comparator.comparing(PortableAssetDocument.License::licenseId);

    private final VersionedAssetType type;
    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    CanonicalPortableAssetAdapter(
            VersionedAssetType type,
            CanonicalJson canonicalJson,
            SmCryptoService crypto) {
        this.type = type;
        this.canonicalJson = canonicalJson;
        this.crypto = crypto;
    }

    @Override
    public VersionedAssetType assetType() {
        return type;
    }

    @Override
    public PortableAssetFile export(PortableAssetDocument.ExportInput input) {
        if (input == null || input.assetType() != type) {
            throw invalid("适配器输入资产类型与登记类型不一致: " + type);
        }
        JsonNode content = canonicalJson.normalize(input.content());
        String contentDigest = digest(canonicalJson.encode(content));
        List<PortableAssetDocument.Source> sources = copySources(input.sources());
        List<PortableAssetDocument.License> licenses = copyLicenses(input.licenses());
        List<PortableAssetDocument.Dependency> dependencies = copyDependencies(input.dependencies());
        List<PortableAssetDocument.TestVector> testVectors = copyTests(input.testVectors());
        PortableAssetDocument document = new PortableAssetDocument(
            SCHEMA_VERSION,
            type,
            input.assetIdentity(),
            input.versionId(),
            input.versionNo(),
            input.organizationScope(),
            input.applicableScope(),
            contentDigest,
            content,
            sources,
            licenses,
            dependencies,
            input.validation(),
            testVectors);
        PortableAssetDocument validated = validateDocument(document);
        byte[] bytes = canonicalJson.encode(validated);
        return new PortableAssetFile(path(validated), bytes, digest(bytes));
    }

    @Override
    public PortableAssetDocument validate(byte[] bytes) {
        return validateDocument(canonicalJson.decodeCanonical(bytes, PortableAssetDocument.class));
    }

    @Override
    public void materialize(byte[] bytes, PortableAssetMaterializationTarget target) {
        if (target == null) {
            throw invalid("资产事务物化端口不能为空");
        }
        target.materialize(validate(bytes));
    }

    private PortableAssetDocument validateDocument(PortableAssetDocument document) {
        if (document == null || !SCHEMA_VERSION.equals(document.schemaVersion())) {
            throw invalid("资产文档 schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
        if (document.assetType() != type) {
            throw invalid("资产文档类型与适配器登记类型不一致: " + type);
        }
        requireStableId(document.assetIdentity(), "assetIdentity");
        requireStableId(document.versionId(), "versionId");
        requireText(document.versionNo(), "versionNo");
        requireText(document.organizationScope(), "organizationScope");
        requireText(document.applicableScope(), "applicableScope");
        if (document.content() == null || document.content().isNull()
                || (!document.content().isObject() && !document.content().isArray())
                || document.content().isEmpty()) {
            throw invalid("资产必须携带可恢复的非空结构化正文");
        }
        String actualContentDigest = digest(canonicalJson.encode(document.content()));
        if (!actualContentDigest.equals(document.contentDigest())) {
            throw invalid("资产正文摘要与规范正文不一致: " + document.assetIdentity());
        }
        Set<String> licenseIds = validateLicenses(document.licenses());
        validateSources(document.sources(), licenseIds);
        validateDependencies(document.dependencies());
        validateProof(document.validation(), document.versionId());
        validateTests(document.testVectors());
        if (type == VersionedAssetType.RULE) {
            AssetSelfContainmentPolicy.requireRuleSelfContained(document.content());
        }
        if (type == VersionedAssetType.PATHWAY) {
            AssetSelfContainmentPolicy.requirePathwaySelfContained(document.content());
        }
        return new PortableAssetDocument(
            SCHEMA_VERSION,
            type,
            document.assetIdentity(),
            document.versionId(),
            document.versionNo(),
            document.organizationScope(),
            document.applicableScope(),
            document.contentDigest(),
            canonicalJson.normalize(document.content()),
            List.copyOf(document.sources()),
            List.copyOf(document.licenses()),
            List.copyOf(document.dependencies()),
            document.validation(),
            List.copyOf(document.testVectors()));
    }

    private void validateSources(
            List<PortableAssetDocument.Source> sources,
            Set<String> licenseIds) {
        if (sources == null || sources.isEmpty()) {
            throw invalid("资产缺少可追溯来源");
        }
        Set<String> unique = new HashSet<>();
        for (PortableAssetDocument.Source source : sources) {
            if (source == null) {
                throw invalid("资产来源条目不能为空");
            }
            requireStableId(source.sourceType(), "sourceType");
            requireText(source.title(), "source.title");
            requireText(source.sourceVersion(), "source.sourceVersion");
            requireText(source.citationAnchor(), "source.citationAnchor");
            requireDigest(source.originalDigest(), "source.originalDigest");
            requireStableId(source.licenseId(), "source.licenseId");
            if (!licenseIds.contains(source.licenseId())) {
                throw invalid("资产来源引用了包内不存在的许可: " + source.licenseId());
            }
            String key = source.sourceType() + "|" + source.title() + "|"
                + source.sourceVersion() + "|" + source.citationAnchor();
            if (!unique.add(key)) {
                throw invalid("资产来源与引用锚点重复: " + key);
            }
        }
        if (!sources.equals(sources.stream().sorted(SOURCE_ORDER).toList())) {
            throw invalid("资产来源未按稳定引用键排序");
        }
    }

    private Set<String> validateLicenses(List<PortableAssetDocument.License> licenses) {
        if (licenses == null || licenses.isEmpty()) {
            throw invalid("资产缺少允许向目标医院交付的再分发许可");
        }
        Set<String> licenseIds = new HashSet<>();
        for (PortableAssetDocument.License license : licenses) {
            if (license == null || !license.redistributionAllowed()) {
                throw invalid("资产许可条目缺失或不允许向目标医院再分发");
            }
            requireStableId(license.licenseId(), "licenseId");
            requireText(license.redistributionScope(), "redistributionScope");
            requireDigest(license.licenseDigest(), "licenseDigest");
            if (!licenseIds.add(license.licenseId())) {
                throw invalid("资产许可标识重复: " + license.licenseId());
            }
        }
        if (!licenses.equals(licenses.stream().sorted(LICENSE_ORDER).toList())) {
            throw invalid("资产许可未按稳定标识排序");
        }
        return licenseIds;
    }

    private void validateDependencies(List<PortableAssetDocument.Dependency> dependencies) {
        if (dependencies == null) {
            throw invalid("资产精确依赖列表不能为空");
        }
        Set<String> unique = new HashSet<>();
        for (PortableAssetDocument.Dependency dependency : dependencies) {
            if (dependency == null || dependency.assetType() == null || dependency.dependencyKind() == null) {
                throw invalid("资产依赖缺少类型或依赖语义");
            }
            requireStableId(dependency.assetIdentity(), "dependency.assetIdentity");
            requireStableId(dependency.versionId(), "dependency.versionId");
            requireText(dependency.versionNo(), "dependency.versionNo");
            requireDigest(dependency.contentDigest(), "dependency.contentDigest");
            String key = dependency.assetType() + "|" + dependency.assetIdentity() + "|" + dependency.versionId();
            if (!unique.add(key)) {
                throw invalid("资产精确依赖重复: " + key);
            }
        }
        if (!dependencies.equals(dependencies.stream().sorted(DEPENDENCY_ORDER).toList())) {
            throw invalid("资产精确依赖未按稳定键排序");
        }
    }

    private void validateProof(
            PortableAssetDocument.Validation validation,
            String versionId) {
        if (validation == null || !validation.passed()) {
            throw invalid("资产缺少发布前通过的类型校验事实");
        }
        requireStableId(validation.profile(), "validation.profile");
        requireStableId(validation.versionId(), "validation.versionId");
        if (!versionId.equals(validation.versionId())) {
            throw invalid("类型校验事实未绑定当前不可变资产版本");
        }
        requireDigest(validation.resultDigest(), "validation.resultDigest");
    }

    private void validateTests(List<PortableAssetDocument.TestVector> tests) {
        if (tests == null || tests.isEmpty()) {
            throw invalid("资产至少必须携带一个可重放合成测试向量");
        }
        Set<String> unique = new HashSet<>();
        for (PortableAssetDocument.TestVector test : tests) {
            if (test == null || test.input() == null || test.input().isNull()
                    || test.expected() == null || test.expected().isNull()
                    || test.syntheticProvenance() == null) {
                throw invalid("测试向量必须具有输入、预期结果和确定性合成生成证明");
            }
            requireStableId(test.vectorId(), "testVector.vectorId");
            validateSyntheticProvenance(test.syntheticProvenance());
            if (!unique.add(test.vectorId())) {
                throw invalid("测试向量标识重复: " + test.vectorId());
            }
        }
        if (!tests.equals(tests.stream().sorted(TEST_ORDER).toList())) {
            throw invalid("测试向量未按稳定标识排序");
        }
    }

    private void validateSyntheticProvenance(
            PortableAssetDocument.SyntheticProvenance provenance) {
        requireStableId(provenance.generatorId(), "synthetic.generatorId");
        requireText(provenance.generatorVersion(), "synthetic.generatorVersion");
        requireStableId(provenance.scenarioId(), "synthetic.scenarioId");
        requireDigest(provenance.manifestDigest(), "synthetic.manifestDigest");
    }

    private List<PortableAssetDocument.Source> copySources(
            List<PortableAssetDocument.Source> sources) {
        if (sources == null) {
            return null;
        }
        if (sources.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("资产来源条目不能为空");
        }
        return sources.stream().sorted(SOURCE_ORDER).toList();
    }

    private List<PortableAssetDocument.License> copyLicenses(
            List<PortableAssetDocument.License> licenses) {
        if (licenses == null) {
            return null;
        }
        if (licenses.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("资产许可条目不能为空");
        }
        return licenses.stream().sorted(LICENSE_ORDER).toList();
    }

    private List<PortableAssetDocument.Dependency> copyDependencies(
            List<PortableAssetDocument.Dependency> dependencies) {
        if (dependencies == null) {
            return null;
        }
        if (dependencies.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("资产依赖条目不能为空");
        }
        return dependencies.stream().sorted(DEPENDENCY_ORDER).toList();
    }

    private List<PortableAssetDocument.TestVector> copyTests(
            List<PortableAssetDocument.TestVector> tests) {
        if (tests == null) {
            return null;
        }
        List<PortableAssetDocument.TestVector> normalized = new ArrayList<>();
        for (PortableAssetDocument.TestVector test : tests) {
            if (test == null) {
                throw invalid("测试向量条目不能为空");
            }
            normalized.add(new PortableAssetDocument.TestVector(
                test.vectorId(),
                canonicalJson.normalize(test.input()),
                canonicalJson.normalize(test.expected()),
                test.syntheticProvenance()));
        }
        normalized.sort(TEST_ORDER);
        return List.copyOf(normalized);
    }

    private String path(PortableAssetDocument document) {
        return "assets/" + type.name() + "/" + document.assetIdentity() + "/"
            + document.versionId() + ".json";
    }

    private String digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    private static void requireStableId(String value, String label) {
        if (value == null || !value.equals(value.trim()) || !STABLE_ID.matcher(value).matches()) {
            throw invalid(label + " 必须是稳定标识");
        }
    }

    private static void requireDigest(String value, String label) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw invalid(label + " 必须是带算法前缀的 256 位摘要");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid(label + " 不能为空且不能包含首尾空白");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
