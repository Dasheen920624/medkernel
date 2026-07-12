package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;

/** 13 类正文、发布状态、依赖和合成测试安全边界的整包装配合同测试。 */
class FullPackageAssemblerTest {

    private static final String DIGEST = "sm3:" + "a".repeat(64);
    private static final String SHA256 = "sha256:" + "b".repeat(64);

    private final ObjectMapper json = new ObjectMapper();
    private final SmCryptoService crypto = new SmCryptoService();
    private final PortableAssetAdapterRegistry adapters =
        new PortableAssetAdapterRegistry(json, crypto);
    private final FullPackageReleaseDocumentCodec releases =
        new FullPackageReleaseDocumentCodec(json, crypto);
    private final FullPackageAssembler assembler =
        new FullPackageAssembler(adapters, releases);

    @Test
    void assemblesExactlyAllThirteenActiveTypesAndCanonicalReleaseState() throws Exception {
        FullPackageSnapshot snapshot = completeSnapshot();

        AssembledFullPackage assembled = assembler.assemble(snapshot);

        assertThat(assembled.platformReleaseIdentity()).isEqualTo("baseline-release-0008");
        assertThat(assembled.files()).hasSize(14);
        assertThat(assembled.files())
            .extracting(PortableAssetFile::path)
            .contains("release/platform-release.json");
        FullPackageReleaseDocument release = releases.decode(assembled.files().stream()
            .filter(file -> file.path().equals("release/platform-release.json"))
            .findFirst()
            .orElseThrow()
            .bytes());
        assertThat(release.entries()).hasSize(14);
        assertThat(release.entries().stream()
            .filter(entry -> entry.state() == ReleaseEntryState.ACTIVE)
            .map(FullPackageReleaseDocument.Entry::assetType))
            .containsExactlyInAnyOrder(VersionedAssetType.values());
        assertThat(release.entries().stream()
            .filter(entry -> entry.state() == ReleaseEntryState.DISABLED)
            .map(FullPackageReleaseDocument.Entry::assetIdentity))
            .containsExactly("ASSET.RETIRED");
        assertThat(release.withdrawals()).containsExactly(new FullPackageReleaseDocument.Withdrawal(
            VersionedAssetType.KNOWLEDGE,
            "ASSET.RETIRED",
            "version-retired-1",
            "version-knowledge-1",
            DIGEST));
    }

    @Test
    void rejectsMissingAssetTypeAndDirectPersonIdentifierInSyntheticTestVector()
            throws Exception {
        FullPackageSnapshot complete = completeSnapshot();
        FullPackageSnapshot missingType = new FullPackageSnapshot(
            complete.platformReleaseIdentity(),
            complete.revisionNo(),
            complete.platformManifestSha256(),
            complete.entries().stream()
                .filter(entry -> entry.assetType() != VersionedAssetType.ACTION_CARD)
                .toList(),
            complete.activeAssets().stream()
                .filter(input -> input.assetType() != VersionedAssetType.ACTION_CARD)
                .toList(),
            complete.withdrawals());

        assertValidation(() -> assembler.assemble(missingType), "13 类");

        List<PortableAssetDocument.ExportInput> sensitive =
            new ArrayList<>(complete.activeAssets());
        PortableAssetDocument.ExportInput original = sensitive.getFirst();
        sensitive.set(0, new PortableAssetDocument.ExportInput(
            original.assetType(),
            original.assetIdentity(),
            original.versionId(),
            original.versionNo(),
            original.organizationScope(),
            original.applicableScope(),
            original.content(),
            original.sources(),
            original.licenses(),
            original.dependencies(),
            original.validation(),
            List.of(new PortableAssetDocument.TestVector(
                "vector-sensitive",
                json.readTree("{\"patientId\":\"real-123456\"}"),
                json.readTree("{\"matched\":true}"),
                new PortableAssetDocument.SyntheticProvenance(
                    "generator-medkernel",
                    "1.0.0",
                    "scenario-sensitive",
                    DIGEST)))));
        FullPackageSnapshot unsafe = new FullPackageSnapshot(
            complete.platformReleaseIdentity(),
            complete.revisionNo(),
            complete.platformManifestSha256(),
            complete.entries(),
            sensitive,
            complete.withdrawals());

        assertValidation(() -> assembler.assemble(unsafe), "患者标识");
    }

    @Test
    void returnsTypedValidationFailureForMalformedSnapshotInsteadOfNullPointer() throws Exception {
        FullPackageSnapshot complete = completeSnapshot();
        List<PortableAssetDocument.ExportInput> malformedAssets =
            new ArrayList<>(complete.activeAssets());
        PortableAssetDocument.ExportInput original = malformedAssets.getFirst();
        malformedAssets.set(0, new PortableAssetDocument.ExportInput(
            original.assetType(),
            original.assetIdentity(),
            original.versionId(),
            original.versionNo(),
            original.organizationScope(),
            original.applicableScope(),
            original.content(),
            original.sources(),
            original.licenses(),
            original.dependencies(),
            original.validation(),
            null));
        FullPackageSnapshot malformed = new FullPackageSnapshot(
            complete.platformReleaseIdentity(),
            complete.revisionNo(),
            complete.platformManifestSha256(),
            complete.entries(),
            malformedAssets,
            complete.withdrawals());

        assertValidation(() -> assembler.assemble(malformed), "测试向量");
    }

    @Test
    void releaseCodecReturnsTypedValidationFailureForMissingActiveDigest() {
        FullPackageReleaseDocument malformed = new FullPackageReleaseDocument(
            "1.0",
            "baseline-release-0008",
            8,
            SHA256,
            List.of(new FullPackageReleaseDocument.Entry(
                VersionedAssetType.KNOWLEDGE,
                "ASSET.KNOWLEDGE",
                ReleaseEntryState.ACTIVE,
                "version-knowledge-1",
                "1.0.0",
                null,
                DIGEST,
                "assets/KNOWLEDGE/knowledge.json")),
            List.of());

        assertValidation(() -> releases.encode(malformed), "来源正文");
    }

    @Test
    void rejectsCredentialMaterialWithAlternateJsonKeySpelling() throws Exception {
        FullPackageSnapshot complete = completeSnapshot();
        List<PortableAssetDocument.ExportInput> unsafeAssets =
            new ArrayList<>(complete.activeAssets());
        PortableAssetDocument.ExportInput original = unsafeAssets.getFirst();
        unsafeAssets.set(0, new PortableAssetDocument.ExportInput(
            original.assetType(),
            original.assetIdentity(),
            original.versionId(),
            original.versionNo(),
            original.organizationScope(),
            original.applicableScope(),
            json.readTree("{\"body\":\"完整正文\",\"client_secret\" : \"sensitive\"}"),
            original.sources(),
            original.licenses(),
            original.dependencies(),
            original.validation(),
            original.testVectors()));
        FullPackageSnapshot unsafe = new FullPackageSnapshot(
            complete.platformReleaseIdentity(),
            complete.revisionNo(),
            complete.platformManifestSha256(),
            complete.entries(),
            unsafeAssets,
            complete.withdrawals());

        assertValidation(() -> assembler.assemble(unsafe), "私钥或凭据");
    }

    private FullPackageSnapshot completeSnapshot() throws Exception {
        List<FullPackageSnapshot.Entry> entries = new ArrayList<>();
        List<PortableAssetDocument.ExportInput> assets = new ArrayList<>();
        for (VersionedAssetType type : VersionedAssetType.values()) {
            String suffix = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            entries.add(new FullPackageSnapshot.Entry(
                type,
                "ASSET." + type.name(),
                ReleaseEntryState.ACTIVE,
                "version-" + suffix + "-1",
                "1.0.0",
                SHA256));
            assets.add(input(type, suffix));
        }
        entries.add(new FullPackageSnapshot.Entry(
            VersionedAssetType.KNOWLEDGE,
            "ASSET.RETIRED",
            ReleaseEntryState.DISABLED,
            null,
            null,
            null));
        return new FullPackageSnapshot(
            "baseline-release-0008",
            8,
            SHA256,
            entries,
            assets,
            List.of(new FullPackageSnapshot.Withdrawal(
                VersionedAssetType.KNOWLEDGE,
                "ASSET.RETIRED",
                "version-retired-1",
                "version-knowledge-1",
                DIGEST)));
    }

    private PortableAssetDocument.ExportInput input(
            VersionedAssetType type,
            String suffix) throws Exception {
        String versionId = "version-" + suffix + "-1";
        return new PortableAssetDocument.ExportInput(
            type,
            "ASSET." + type.name(),
            versionId,
            "1.0.0",
            "/PLATFORM",
            "ALL",
            json.readTree("{\"schemaVersion\":\"1.0\",\"body\":\"完整正文-" + suffix + "\"}"),
            List.of(new PortableAssetDocument.Source(
                "GUIDELINE",
                "获准指南-" + suffix,
                "2026.1",
                "section-1",
                SHA256,
                "license-redistributable")),
            List.of(new PortableAssetDocument.License(
                "license-redistributable",
                true,
                "AUTHORIZED_HOSPITALS",
                SHA256)),
            List.of(),
            new PortableAssetDocument.Validation(
                "profile-" + suffix,
                true,
                versionId,
                DIGEST),
            List.of(new PortableAssetDocument.TestVector(
                "vector-" + suffix,
                json.readTree("{\"syntheticCase\":\"" + suffix + "\"}"),
                json.readTree("{\"matched\":true}"),
                new PortableAssetDocument.SyntheticProvenance(
                    "generator-medkernel",
                    "1.0.0",
                    "scenario-" + suffix,
                    DIGEST))));
    }

    private void assertValidation(ThrowingCall call, String message) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(exception.getMessage()).contains(message);
            });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
