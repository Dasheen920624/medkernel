package com.medkernel.engine.knowledge.authority;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.delivery.FullPackageManifest;
import com.medkernel.engine.knowledge.delivery.FullPackageManifestCodec;
import com.medkernel.engine.knowledge.delivery.FullPackageReleaseDocument;
import com.medkernel.engine.knowledge.delivery.FullPackageReleaseDocumentCodec;
import com.medkernel.engine.knowledge.delivery.FullPackageReleaseIntegrity;
import com.medkernel.engine.knowledge.delivery.PackageSignatureEnvelopeCodec;
import com.medkernel.engine.knowledge.delivery.PortableAssetAdapterRegistry;
import com.medkernel.engine.knowledge.delivery.PortableAssetDocument;
import com.medkernel.engine.knowledge.delivery.PortableAssetFile;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.crypto.SmCryptoService;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/** 为导出、上传和双实例测试生成真实 SM2/SM3 自包含 `.mkp`，不保存固定私钥。 */
public final class FullPackageTestFixture {

    public static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    public static final String ISSUER_ID = "issuer-platform-134";
    public static final String RELEASE_ID = "baseline-release-0001";
    public static final String SHA256 = "sha256:" + "b".repeat(64);
    public static final String DIGEST = "sm3:" + "a".repeat(64);
    public static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final String RELEASE_PATH = "release/platform-release.json";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SmCryptoService crypto = new SmCryptoService();
    private final PortableAssetAdapterRegistry adapters =
        new PortableAssetAdapterRegistry(json, crypto);
    private final FullPackageManifestCodec manifests =
        new FullPackageManifestCodec(json, crypto);
    private final FullPackageReleaseDocumentCodec releases =
        new FullPackageReleaseDocumentCodec(json, crypto);
    private final PackageSignatureEnvelopeCodec signatures =
        new PackageSignatureEnvelopeCodec(json);
    private final InMemorySigningAdapter signing =
        new InMemorySigningAdapter(Clock.fixed(NOW, ZoneOffset.UTC));
    private final SigningKeyPort.ProvisionedSigningKey key =
        signing.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);

    public SignedPackage build(String deliveryId, long releaseSequence) {
        return build(PackageOptions.defaults(deliveryId, releaseSequence));
    }

    public SignedPackage build(PackageOptions options) {
        try {
            List<PortableAssetFile> files = new ArrayList<>();
            List<PortableAssetDocument> documents = new ArrayList<>();
            List<FullPackageReleaseDocument.Entry> releaseEntries = new ArrayList<>();
            for (VersionedAssetType type : VersionedAssetType.values()) {
                String suffix = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                String identity = "ASSET." + type.name();
                String versionId = "version-" + suffix + "-1";
                List<PortableAssetDocument.Dependency> dependencies =
                    options.missingDependency() && type == VersionedAssetType.KNOWLEDGE
                        ? List.of(new PortableAssetDocument.Dependency(
                            VersionedAssetType.RULE,
                            "ASSET.MISSING",
                            "version-missing-1",
                            "V1",
                            DIGEST,
                            AssetDependencyKind.RUNTIME_ASSET))
                        : List.of();
                String content = options.patientIdentifier()
                    ? "{\"patientId\":\"real-123456\",\"body\":\"" + options.contentMarker() + "\"}"
                    : "{\"schemaVersion\":\"1.0\",\"body\":\"完整正文-"
                        + suffix + "-" + options.contentMarker() + "\"}";
                PortableAssetFile file = adapters.require(type).export(
                    new PortableAssetDocument.ExportInput(
                        type,
                        identity,
                        versionId,
                        "V1",
                        "/PLATFORM",
                        "ALL",
                        AssetVersionSafetyPolicy.NORMAL,
                        AssetVersionOverridePolicy.FREE,
                        json.readTree(content),
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
                            options.licenseScope(),
                            SHA256)),
                        dependencies,
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
                                DIGEST)))));
                PortableAssetDocument document = adapters.require(type).validate(file.bytes());
                files.add(file);
                documents.add(document);
                releaseEntries.add(new FullPackageReleaseDocument.Entry(
                    type,
                    identity,
                    ReleaseEntryState.ACTIVE,
                    versionId,
                    "V1",
                    options.contentHashMismatch() && type == VersionedAssetType.KNOWLEDGE
                        ? "sha256:" + "c".repeat(64)
                        : "sha256:" + document.contentSha256(),
                    document.contentDigest(),
                    file.path()));
            }
            releaseEntries.add(new FullPackageReleaseDocument.Entry(
                VersionedAssetType.KNOWLEDGE,
                "ASSET.RETIRED",
                ReleaseEntryState.DISABLED,
                null,
                null,
                null,
                null,
                null));
            FullPackageReleaseDocument release = new FullPackageReleaseDocument(
                "1.0",
                RELEASE_ID,
                1,
                options.manifestHashMismatch()
                    ? "sha256:" + "d".repeat(64)
                    : "sha256:" + FullPackageReleaseIntegrity.manifestSha256(releaseEntries),
                releaseEntries,
                List.of(new FullPackageReleaseDocument.Withdrawal(
                    VersionedAssetType.KNOWLEDGE,
                    "ASSET.RETIRED",
                    "version-retired-1",
                    "version-knowledge-1",
                    DIGEST)));
            byte[] releaseBytes = releases.encode(release);
            files.add(new PortableAssetFile(
                RELEASE_PATH,
                releaseBytes,
                digest(releaseBytes)));
            files.sort(java.util.Comparator.comparing(PortableAssetFile::path));

            FullPackageManifest manifest = new FullPackageManifest(
                "1.0",
                MedicalPackageType.FULL,
                options.deliveryId(),
                AUTHORITY_ID,
                ISSUER_ID,
                key.keyId(),
                options.releaseSequence(),
                RELEASE_ID,
                null,
                options.compatibility(),
                files.stream()
                    .map(file -> new FullPackageManifest.FileEntry(
                        file.path(), file.bytes().length, file.sm3Digest()))
                    .toList());
            byte[] manifestBytes = manifests.encode(manifest);
            String manifestDigest = manifests.sm3Digest(manifestBytes);
            PackageSignatureEnvelope unsigned = new PackageSignatureEnvelope(
                AUTHORITY_ID,
                ISSUER_ID,
                key.keyId(),
                key.rootFingerprint(),
                options.releaseSequence(),
                manifestDigest,
                key.certificateChainPem(),
                NOW,
                "");
            PackageSignatureEnvelope envelope = new PackageSignatureEnvelope(
                unsigned.authorityId(),
                unsigned.issuerInstanceId(),
                unsigned.keyId(),
                unsigned.rootFingerprint(),
                unsigned.releaseSequence(),
                unsigned.manifestDigest(),
                unsigned.certificateChainPem(),
                unsigned.signedAt(),
                crypto.base64Encode(signing.sign(
                    AUTHORITY_ID,
                    ISSUER_ID,
                    key.keyId(),
                    unsigned.canonicalPayload())));
            Map<String, byte[]> entries = new TreeMap<>();
            for (PortableAssetFile file : files) {
                entries.put(file.path(), file.bytes());
            }
            entries.put("manifest.json", manifestBytes);
            entries.put("signature.json", signatures.encode(envelope));
            return new SignedPackage(
                write(entries, ZipEntry.STORED),
                manifests.decode(manifestBytes),
                envelope,
                releases.decode(releaseBytes),
                List.copyOf(documents),
                files.getFirst().path());
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成完整医疗资源包测试制品", exception);
        }
    }

    public String rootFingerprint() {
        return key.rootFingerprint();
    }

    public String rootCertificatePem() {
        int rootStart = key.certificateChainPem().lastIndexOf("-----BEGIN CERTIFICATE-----");
        return key.certificateChainPem().substring(rootStart);
    }

    public byte[] tamperEntry(SignedPackage source, String path) {
        return rewrite(source.bytes(), path, bytes -> {
            byte[] changed = bytes.clone();
            changed[changed.length - 1] ^= 1;
            return changed;
        }, ZipEntry.STORED);
    }

    public byte[] asDeflated(SignedPackage source) {
        return rewrite(source.bytes(), null, UnaryOperator.identity(), ZipEntry.DEFLATED);
    }

    public byte[] withTraversalEntry(SignedPackage source) {
        Map<String, byte[]> entries = readEntries(source.bytes());
        entries.put("../escape.json", "{}".getBytes(StandardCharsets.UTF_8));
        return write(entries, ZipEntry.STORED);
    }

    public byte[] withSymbolicLinkEntry(SignedPackage source) {
        try {
            Map<String, byte[]> entries = readEntries(source.bytes());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                    addStored(output, item.getKey(), item.getValue(), false);
                }
                addStored(output, "assets/KNOWLEDGE/link.json", "../../escape".getBytes(StandardCharsets.UTF_8), true);
                output.finish();
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成符号链接容器测试制品", exception);
        }
    }

    private void addStored(
            ZipArchiveOutputStream output,
            String name,
            byte[] bytes,
            boolean symbolicLink) throws Exception {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        entry.setTime(0L);
        if (symbolicLink) {
            entry.setUnixMode(UnixStat.LINK_FLAG | 0777);
        }
        output.putArchiveEntry(entry);
        output.write(bytes);
        output.closeArchiveEntry();
    }

    private byte[] rewrite(
            byte[] source,
            String changedPath,
            UnaryOperator<byte[]> transformer,
            int method) {
        Map<String, byte[]> entries = readEntries(source);
        if (changedPath != null) {
            byte[] current = entries.get(changedPath);
            if (current == null) {
                throw new IllegalArgumentException("测试包条目不存在: " + changedPath);
            }
            entries.put(changedPath, transformer.apply(current));
        } else {
            entries.replaceAll((ignored, bytes) -> transformer.apply(bytes));
        }
        return write(entries, method);
    }

    private Map<String, byte[]> readEntries(byte[] source) {
        try {
            Map<String, byte[]> result = new TreeMap<>();
            try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(source), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    result.put(entry.getName(), input.readAllBytes());
                    input.closeEntry();
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取完整医疗资源包测试制品", exception);
        }
    }

    private byte[] write(Map<String, byte[]> entries, int method) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                output.setMethod(method);
                for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                    ZipEntry entry = new ZipEntry(item.getKey());
                    entry.setTimeLocal(ZIP_TIME);
                    if (method == ZipEntry.STORED) {
                        CRC32 crc = new CRC32();
                        crc.update(item.getValue());
                        entry.setMethod(ZipEntry.STORED);
                        entry.setSize(item.getValue().length);
                        entry.setCompressedSize(item.getValue().length);
                        entry.setCrc(crc.getValue());
                    }
                    output.putNextEntry(entry);
                    output.write(item.getValue());
                    output.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入完整医疗资源包测试制品", exception);
        }
    }

    private String digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    public record PackageOptions(
        String deliveryId,
        long releaseSequence,
        FullPackageManifest.Compatibility compatibility,
        String contentMarker,
        boolean missingDependency,
        boolean patientIdentifier,
        String licenseScope,
        boolean contentHashMismatch,
        boolean manifestHashMismatch
    ) {
        public static PackageOptions defaults(String deliveryId, long releaseSequence) {
            return new PackageOptions(
                deliveryId,
                releaseSequence,
                new FullPackageManifest.Compatibility("1.0", "1.0.0", "1.x", "V1", "V1"),
                "default",
                false,
                false,
                "AUTHORIZED_HOSPITALS",
                false,
                false);
        }
    }

    public record SignedPackage(
        byte[] bytes,
        FullPackageManifest manifest,
        PackageSignatureEnvelope envelope,
        FullPackageReleaseDocument release,
        List<PortableAssetDocument> documents,
        String firstAssetPath
    ) {
        public SignedPackage {
            bytes = bytes.clone();
            documents = List.copyOf(documents);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
