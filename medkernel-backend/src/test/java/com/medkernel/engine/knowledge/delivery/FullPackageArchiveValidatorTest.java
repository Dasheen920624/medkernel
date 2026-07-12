package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture.PackageOptions;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture.SignedPackage;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** `.mkp` 容器边界、逐文件事实、13 类正文和兼容性的纯只读预检合同。 */
class FullPackageArchiveValidatorTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SmCryptoService crypto = new SmCryptoService();
    private final FullPackageTestFixture packages = new FullPackageTestFixture();
    private FullPackageQuarantineStore quarantine;
    private FullPackageArchiveValidator validator;

    @BeforeEach
    void setUp() {
        FullPackageImportProperties properties = new FullPackageImportProperties(
            temporaryDirectory.resolve("quarantine").toString(),
            32L * 1024 * 1024,
            64,
            4L * 1024 * 1024,
            32L * 1024 * 1024,
            10,
            "1.0",
            "1.0.0",
            "V1");
        quarantine = new FullPackageQuarantineStore(properties, crypto);
        validator = new FullPackageArchiveValidator(
            properties,
            new FullPackageManifestCodec(json, crypto),
            new PackageSignatureEnvelopeCodec(json),
            new FullPackageReleaseDocumentCodec(json, crypto),
            new PortableAssetAdapterRegistry(json, crypto),
            new PortablePackageContentPolicy(),
            crypto);
    }

    @Test
    void validatesCanonicalArchiveAndAllThirteenSelfContainedAssetTypes() {
        SignedPackage source = packages.build("mkp-full-000001", 1);

        FullPackageInspection inspected = inspect(source.bytes());

        assertThat(inspected.manifest()).isEqualTo(source.manifest());
        assertThat(inspected.signatureEnvelope()).isEqualTo(source.envelope());
        assertThat(inspected.releaseDocument()).isEqualTo(source.release());
        assertThat(inspected.documents()).hasSize(VersionedAssetType.values().length);
        assertThat(inspected.documents())
            .extracting(PortableAssetDocument::assetType)
            .containsExactlyInAnyOrder(VersionedAssetType.values());
        assertThat(inspected.archiveEntryCount()).isEqualTo(16);
        assertThat(inspected.expandedBytes()).isPositive();
    }

    @Test
    void rejectsTamperedDeclaredBytesBeforeAnyContentCanBeUsed() {
        SignedPackage source = packages.build("mkp-full-000001", 1);

        assertRejected(
            packages.tamperEntry(source, source.firstAssetPath()),
            "摘要");
    }

    @Test
    void rejectsTraversalSymbolicLinksAndNonCanonicalCompressedContainers() {
        SignedPackage source = packages.build("mkp-full-000001", 1);

        assertRejected(packages.withTraversalEntry(source), "路径");
        assertRejected(packages.withSymbolicLinkEntry(source), "符号链接");
        assertRejected(packages.asDeflated(source), "无压缩");
    }

    @Test
    void rejectsIncompatibleEngineOrDatabaseRange() {
        PackageOptions defaults = PackageOptions.defaults("mkp-full-000001", 1);
        SignedPackage incompatible = packages.build(new PackageOptions(
            defaults.deliveryId(),
            defaults.releaseSequence(),
            new FullPackageManifest.Compatibility("1.0", "2.0.0", "2.x", "V2", "V2"),
            defaults.contentMarker(),
            false,
            false,
            defaults.licenseScope(),
            false,
            false));

        assertRejected(incompatible.bytes(), "兼容");
    }

    @Test
    void rejectsMissingExactDependencyAndTargetLicenseMismatch() {
        PackageOptions defaults = PackageOptions.defaults("mkp-full-000001", 1);
        SignedPackage missingDependency = packages.build(new PackageOptions(
            defaults.deliveryId(),
            defaults.releaseSequence(),
            defaults.compatibility(),
            defaults.contentMarker(),
            true,
            false,
            defaults.licenseScope(),
            false,
            false));
        SignedPackage wrongTarget = packages.build(new PackageOptions(
            "mkp-full-000002",
            2,
            defaults.compatibility(),
            defaults.contentMarker(),
            false,
            false,
            "HOSPITAL:hospital-B",
            false,
            false));

        assertRejected(missingDependency.bytes(), "依赖");
        assertRejected(wrongTarget.bytes(), "目标医院");
    }

    @Test
    void rejectsDirectPatientIdentifierEvenWhenPackageIsCorrectlySigned() {
        PackageOptions defaults = PackageOptions.defaults("mkp-full-000001", 1);
        SignedPackage unsafe = packages.build(new PackageOptions(
            defaults.deliveryId(),
            defaults.releaseSequence(),
            defaults.compatibility(),
            defaults.contentMarker(),
            false,
            true,
            defaults.licenseScope(),
            false,
            false));

        assertRejected(unsafe.bytes(), "患者标识");
    }

    @Test
    void rejectsSignedPackageWhoseRecoverableBodyOrPlatformManifestHashDoesNotMatch() {
        PackageOptions defaults = PackageOptions.defaults("mkp-full-000001", 1);
        SignedPackage bodyMismatch = packages.build(new PackageOptions(
            defaults.deliveryId(), defaults.releaseSequence(), defaults.compatibility(),
            defaults.contentMarker(), false, false, defaults.licenseScope(), true, false));
        SignedPackage manifestMismatch = packages.build(new PackageOptions(
            "mkp-full-000002", 2, defaults.compatibility(),
            defaults.contentMarker(), false, false, defaults.licenseScope(), false, true));

        assertRejected(bodyMismatch.bytes(), "可恢复正文");
        assertRejected(manifestMismatch.bytes(), "平台版本明细");
    }

    private FullPackageInspection inspect(byte[] bytes) {
        QuarantinedFullPackage stored = quarantine.ingest(new ByteArrayInputStream(bytes));
        return validator.inspect(stored, "hospital-A");
    }

    private void assertRejected(byte[] bytes, String message) {
        assertThatThrownBy(() -> inspect(bytes))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode())
                    .isIn(ErrorCode.VALIDATION_FAILED, ErrorCode.CONFLICT);
                assertThat(exception).hasMessageContaining(message);
            });
    }
}
