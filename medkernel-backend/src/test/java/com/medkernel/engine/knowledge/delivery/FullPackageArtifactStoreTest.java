package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.MedicalPackageType;
import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.engine.knowledge.authority.PackageSigningStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 真实 .mkp 受管落盘、重读和下载字节一致性合同测试。 */
class FullPackageArtifactStoreTest {

    private static final String DELIVERY_ID = "mkp-full-000008";
    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final String KEY_ID = "kms:key:issuer-134";
    private static final String ROOT_DIGEST = "sm3:" + "a".repeat(64);
    private static final Instant SIGNED_AT = Instant.parse("2026-07-12T08:00:00Z");

    @TempDir
    Path root;

    @Test
    void writesCanonicalStoredZipRereadsEveryDeclaredByteAndStreamsRegisteredArtifact()
            throws Exception {
        SmCryptoService crypto = new SmCryptoService();
        ObjectMapper json = new ObjectMapper();
        FullPackageManifestCodec manifests = new FullPackageManifestCodec(json, crypto);
        PackageSignatureEnvelopeCodec signatures = new PackageSignatureEnvelopeCodec(json);
        FullPackageArtifactStore store =
            new FullPackageArtifactStore(root, manifests, signatures, crypto);
        byte[] content = "{\"complete\":true}".getBytes(StandardCharsets.UTF_8);
        String contentDigest = digest(crypto, content);
        FullPackageManifest manifest = manifest(content, contentDigest);
        byte[] manifestBytes = manifests.encode(manifest);
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        PackageSignatureEnvelope envelope = new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_DIGEST,
            8,
            manifestDigest,
            "PUBLIC-CERTIFICATE-CHAIN",
            SIGNED_AT,
            "PUBLIC-SM2-SIGNATURE");
        byte[] signatureBytes = signatures.encode(envelope);

        StoredFullPackage stored = store.store(
            manifestBytes,
            signatureBytes,
            List.of(new PortableAssetFile(
                "assets/RULE/rule-a.json", content, contentDigest)));

        RecoveredFullPackage recovered = store.recoverExisting(
                manifestBytes,
                List.of(new PortableAssetFile(
                    "assets/RULE/rule-a.json", content, contentDigest)))
            .orElseThrow();

        assertThat(stored.storageCoordinate())
            .isEqualTo(DELIVERY_ID + "/" + manifestDigest.substring(4) + ".mkp");
        assertThat(stored.packageFileSize()).isEqualTo(Files.size(stored.path()));
        assertThat(stored.packageFileDigest()).isEqualTo(digest(crypto, Files.readAllBytes(stored.path())));
        assertThat(recovered.stored()).isEqualTo(stored);
        assertThat(recovered.envelope()).isEqualTo(envelope);
        try (ZipFile zip = new ZipFile(stored.path().toFile())) {
            assertThat(zip.stream().map(entry -> entry.getName()).toList())
                .containsExactly(
                    "assets/RULE/rule-a.json",
                    "manifest.json",
                    "signature.json");
            assertThat(zip.stream()).allMatch(entry -> entry.getMethod() == java.util.zip.ZipEntry.STORED);
        }

        PackageRegistration registration = registration(stored, manifestDigest);
        byte[] downloaded;
        try (InputStream input = store.openVerified(registration)) {
            downloaded = input.readAllBytes();
        }
        assertThat(downloaded).containsExactly(Files.readAllBytes(stored.path()));
    }

    @Test
    void refusesDownloadAfterManagedFileByteIsChanged() throws Exception {
        SmCryptoService crypto = new SmCryptoService();
        ObjectMapper json = new ObjectMapper();
        FullPackageManifestCodec manifests = new FullPackageManifestCodec(json, crypto);
        PackageSignatureEnvelopeCodec signatures = new PackageSignatureEnvelopeCodec(json);
        FullPackageArtifactStore store =
            new FullPackageArtifactStore(root, manifests, signatures, crypto);
        byte[] content = "{\"complete\":true}".getBytes(StandardCharsets.UTF_8);
        String contentDigest = digest(crypto, content);
        byte[] manifestBytes = manifests.encode(manifest(content, contentDigest));
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        byte[] signatureBytes = signatures.encode(new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_DIGEST,
            8,
            manifestDigest,
            "PUBLIC-CERTIFICATE-CHAIN",
            SIGNED_AT,
            "PUBLIC-SM2-SIGNATURE"));
        StoredFullPackage stored = store.store(
            manifestBytes,
            signatureBytes,
            List.of(new PortableAssetFile(
                "assets/RULE/rule-a.json", content, contentDigest)));
        byte[] tampered = Files.readAllBytes(stored.path());
        tampered[tampered.length - 1] ^= 1;
        Files.write(stored.path(), tampered);

        assertThatThrownBy(() -> store.openVerified(registration(stored, manifestDigest)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void refusesDeliveryDirectorySymbolicLinkThatEscapesManagedRoot() throws Exception {
        Path managed = root.resolve("managed");
        Path outside = root.resolve("outside");
        Files.createDirectories(managed);
        Files.createDirectories(outside);
        Files.createSymbolicLink(managed.resolve(DELIVERY_ID), outside);
        SmCryptoService crypto = new SmCryptoService();
        ObjectMapper json = new ObjectMapper();
        FullPackageManifestCodec manifests = new FullPackageManifestCodec(json, crypto);
        PackageSignatureEnvelopeCodec signatures = new PackageSignatureEnvelopeCodec(json);
        FullPackageArtifactStore store =
            new FullPackageArtifactStore(managed, manifests, signatures, crypto);
        byte[] content = "{\"complete\":true}".getBytes(StandardCharsets.UTF_8);
        String contentDigest = digest(crypto, content);
        byte[] manifestBytes = manifests.encode(manifest(content, contentDigest));
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        byte[] signatureBytes = signatures.encode(new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_DIGEST,
            8,
            manifestDigest,
            "PUBLIC-CERTIFICATE-CHAIN",
            SIGNED_AT,
            "PUBLIC-SM2-SIGNATURE"));

        assertThatThrownBy(() -> store.store(
            manifestBytes,
            signatureBytes,
            List.of(new PortableAssetFile(
                "assets/RULE/rule-a.json", content, contentDigest))))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        try (var files = Files.list(outside)) {
            assertThat(files).isEmpty();
        }
    }

    private FullPackageManifest manifest(byte[] content, String contentDigest) {
        return new FullPackageManifest(
            "1.0",
            MedicalPackageType.FULL,
            DELIVERY_ID,
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            8,
            "baseline-release-0008",
            null,
            new FullPackageManifest.Compatibility(
                "1.0", "1.0.0", "1.x", "V1", "V1"),
            List.of(new FullPackageManifest.FileEntry(
                "assets/RULE/rule-a.json", content.length, contentDigest)));
    }

    private PackageRegistration registration(
            StoredFullPackage stored,
            String manifestDigest) {
        return new PackageRegistration(
            1L,
            "PLATFORM",
            AUTHORITY_ID,
            DELIVERY_ID,
            8,
            manifestDigest,
            "baseline-release-0008",
            stored.packageFileDigest(),
            stored.packageFileSize(),
            stored.storageCoordinate(),
            ISSUER_ID,
            KEY_ID,
            null,
            null,
            null,
            MedicalPackageType.FULL,
            PackageSigningStatus.SIGNED,
            SIGNED_AT,
            SIGNED_AT,
            0L,
            SIGNED_AT,
            "publisher",
            SIGNED_AT,
            "publisher",
            "trace-package");
    }

    private String digest(SmCryptoService crypto, byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }
}
