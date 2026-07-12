package com.medkernel.engine.knowledge.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.bouncycastle.jcajce.provider.digest.SM3;
import org.springframework.stereotype.Service;

/** 对隔离区 `.mkp` 执行不产生业务写入的容器、正文与兼容性校验。 */
@Service
public class FullPackageArchiveValidator {

    private static final String MANIFEST_PATH = "manifest.json";
    private static final String SIGNATURE_PATH = "signature.json";
    private static final String RELEASE_PATH = "release/platform-release.json";
    private static final Pattern PATH_SEGMENT =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");

    private final FullPackageImportProperties properties;
    private final FullPackageManifestCodec manifestCodec;
    private final PackageSignatureEnvelopeCodec signatureCodec;
    private final FullPackageReleaseDocumentCodec releaseCodec;
    private final PortableAssetAdapterRegistry adapters;
    private final PortablePackageContentPolicy contentPolicy;
    private final SmCryptoService crypto;

    /** 建立只读检查器；固定信任根和防重放由下一层信任预检继续裁定。 */
    public FullPackageArchiveValidator(
            FullPackageImportProperties properties,
            FullPackageManifestCodec manifestCodec,
            PackageSignatureEnvelopeCodec signatureCodec,
            FullPackageReleaseDocumentCodec releaseCodec,
            PortableAssetAdapterRegistry adapters,
            PortablePackageContentPolicy contentPolicy,
            SmCryptoService crypto) {
        this.properties = properties;
        this.manifestCodec = manifestCodec;
        this.signatureCodec = signatureCodec;
        this.releaseCodec = releaseCodec;
        this.adapters = adapters;
        this.contentPolicy = contentPolicy;
        this.crypto = crypto;
    }

    /**
     * 检查内容寻址隔离文件；只返回由真实字节推导的事实，不信任上传文件名或客户端元数据。
     */
    public FullPackageInspection inspect(
            QuarantinedFullPackage artifact,
            String targetHospitalId) {
        requireArtifact(artifact);
        if (targetHospitalId == null || targetHospitalId.isBlank()) {
            throw invalid("医疗资源包预检缺少目标医院");
        }
        try (FileChannel channel = FileChannel.open(
                artifact.path(), StandardOpenOption.READ)) {
            String wholeDigest = digest(channel);
            long actualSize = channel.size();
            if (actualSize != artifact.packageFileSize()
                    || !wholeDigest.equals(artifact.packageFileDigest())) {
                throw conflict("隔离区医疗资源包整包大小或摘要已变化");
            }
            channel.position(0);
            try (ZipFile archive = ZipFile.builder()
                    .setSeekableByteChannel(channel)
                    .setIgnoreLocalFileHeader(false)
                    .get()) {
                ArchiveIndex index = indexArchive(archive);
                byte[] manifestBytes = readEntry(archive, index.require(MANIFEST_PATH));
                byte[] signatureBytes = readEntry(archive, index.require(SIGNATURE_PATH));
                FullPackageManifest manifest = manifestCodec.decode(manifestBytes);
                PackageSignatureEnvelope envelope = signatureCodec.decode(signatureBytes);
                validateEnvelopeBinding(manifest, envelope, manifestBytes);
                validateCompatibility(manifest.compatibility());
                validateDeclaredFiles(index, manifest);

                FullPackageReleaseDocument release = null;
                Map<AssetKey, PortableAssetDocument> documents = new HashMap<>();
                Map<AssetKey, FullPackageManifest.FileEntry> assetFiles = new HashMap<>();
                for (FullPackageManifest.FileEntry declared : manifest.files()) {
                    byte[] bytes = readAndVerifyDeclared(
                        archive, index.require(declared.path()), declared);
                    contentPolicy.validateFile(declared.path(), bytes);
                    if (RELEASE_PATH.equals(declared.path())) {
                        release = releaseCodec.decode(bytes);
                        continue;
                    }
                    VersionedAssetType type = typeFromAssetPath(declared.path());
                    PortableAssetDocument document = adapters.require(type).validate(bytes);
                    String expectedPath = assetPath(document);
                    if (!expectedPath.equals(declared.path())) {
                        throw invalid("医疗资源包资产正文路径与稳定身份不一致: "
                            + declared.path());
                    }
                    if (!"/PLATFORM".equals(document.organizationScope())) {
                        throw invalid("完整医疗资源包只允许平台权威组织范围正文: "
                            + document.assetIdentity());
                    }
                    validateTargetLicense(document, targetHospitalId);
                    contentPolicy.validateDocument(document);
                    AssetKey key = new AssetKey(document.assetType(), document.assetIdentity());
                    if (documents.putIfAbsent(key, document) != null) {
                        throw invalid("医疗资源包资产稳定身份重复: " + key);
                    }
                    assetFiles.put(key, declared);
                }
                if (release == null) {
                    throw invalid("医疗资源包缺少平台版本重建文档");
                }
                validateRelease(manifest, release, documents, assetFiles);
                validateDependencyClosure(documents);
                return new FullPackageInspection(
                    artifact,
                    manifest,
                    envelope,
                    release,
                    documents.values().stream()
                        .sorted(java.util.Comparator
                            .comparing((PortableAssetDocument item) -> item.assetType().name())
                            .thenComparing(PortableAssetDocument::assetIdentity))
                        .toList(),
                    index.entries().size(),
                    index.expandedBytes());
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "医疗资源包容器格式无效或无法完整读取",
                exception);
        }
    }

    private void requireArtifact(QuarantinedFullPackage artifact) {
        if (artifact == null || artifact.path() == null
                || artifact.packageFileDigest() == null
                || artifact.packageFileSize() <= 0
                || !Files.isRegularFile(artifact.path(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(artifact.path())) {
            throw invalid("只能预检受管隔离区内的真实普通文件");
        }
    }

    private ArchiveIndex indexArchive(ZipFile archive) throws IOException {
        LinkedHashMap<String, ZipArchiveEntry> indexed = new LinkedHashMap<>();
        long expanded = 0;
        var entries = archive.getEntriesInPhysicalOrder();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (indexed.size() >= properties.maxEntries()) {
                throw invalid("医疗资源包容器条目数量超过配置上限");
            }
            String path = entry.getName();
            requireCanonicalPath(path);
            if (entry.isDirectory()) {
                throw invalid("医疗资源包不得包含目录条目: " + path);
            }
            if (entry.isUnixSymlink()) {
                throw invalid("医疗资源包不得包含符号链接: " + path);
            }
            if (entry.getGeneralPurposeBit().usesEncryption()) {
                throw invalid("医疗资源包不得包含加密条目: " + path);
            }
            if (entry.getMethod() != ZipEntry.STORED) {
                throw invalid("首发医疗资源包必须使用确定性无压缩 ZIP 条目: " + path);
            }
            if (!archive.canReadEntryData(entry)
                    || entry.getSize() <= 0
                    || entry.getCompressedSize() <= 0
                    || entry.getSize() > properties.maxEntryBytes()) {
                throw invalid("医疗资源包条目大小或编码超出安全边界: " + path);
            }
            if (entry.getSize() > entry.getCompressedSize()
                    * (long) properties.maxCompressionRatio()) {
                throw invalid("医疗资源包条目解压比超过配置上限: " + path);
            }
            expanded = safeAdd(expanded, entry.getSize());
            if (expanded > properties.maxExpandedBytes()) {
                throw invalid("医疗资源包总展开大小超过配置上限");
            }
            if (indexed.putIfAbsent(path, entry) != null) {
                throw invalid("医疗资源包容器路径重复: " + path);
            }
        }
        if (indexed.isEmpty()) {
            throw invalid("医疗资源包容器不能为空");
        }
        List<String> physicalOrder = List.copyOf(indexed.keySet());
        List<String> canonicalOrder = physicalOrder.stream().sorted().toList();
        if (!physicalOrder.equals(canonicalOrder)) {
            throw invalid("医疗资源包容器条目未按规范路径排序");
        }
        return new ArchiveIndex(Map.copyOf(indexed), expanded);
    }

    private void validateDeclaredFiles(ArchiveIndex index, FullPackageManifest manifest) {
        Set<String> expected = new HashSet<>();
        expected.add(MANIFEST_PATH);
        expected.add(SIGNATURE_PATH);
        for (FullPackageManifest.FileEntry file : manifest.files()) {
            expected.add(file.path());
        }
        if (expected.size() != manifest.files().size() + 2
                || !expected.equals(index.entries().keySet())) {
            throw invalid("医疗资源包容器条目与 manifest 声明不完全一致");
        }
        if (manifest.files().stream().noneMatch(file -> RELEASE_PATH.equals(file.path()))) {
            throw invalid("医疗资源包 manifest 未声明平台版本重建文档");
        }
    }

    private void validateEnvelopeBinding(
            FullPackageManifest manifest,
            PackageSignatureEnvelope envelope,
            byte[] manifestBytes) {
        if (!manifest.authorityId().equals(envelope.authorityId())
                || !manifest.issuerInstanceId().equals(envelope.issuerInstanceId())
                || !manifest.keyId().equals(envelope.keyId())
                || manifest.releaseSequence() != envelope.releaseSequence()
                || !manifestCodec.sm3Digest(manifestBytes).equals(envelope.manifestDigest())) {
            throw invalid("医疗资源包签名信封未精确绑定 manifest 身份、序号或摘要");
        }
    }

    private byte[] readAndVerifyDeclared(
            ZipFile archive,
            ZipArchiveEntry entry,
            FullPackageManifest.FileEntry declared) throws IOException {
        if (entry.getSize() != declared.size()) {
            throw invalid("医疗资源包条目大小与 manifest 不一致: " + declared.path());
        }
        byte[] bytes = readEntry(archive, entry);
        String actual = "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
        if (!actual.equals(declared.sm3Digest())) {
            throw invalid("医疗资源包条目摘要与 manifest 不一致: " + declared.path());
        }
        return bytes;
    }

    private byte[] readEntry(ZipFile archive, ZipArchiveEntry entry) throws IOException {
        long expected = entry.getSize();
        if (expected <= 0 || expected > properties.maxEntryBytes()) {
            throw invalid("医疗资源包条目读取大小越界: " + entry.getName());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) expected);
        byte[] buffer = new byte[8192];
        long total = 0;
        try (InputStream input = archive.getInputStream(entry)) {
            int length;
            while ((length = input.read(buffer)) != -1) {
                total = safeAdd(total, length);
                if (total > expected || total > properties.maxEntryBytes()) {
                    throw invalid("医疗资源包条目实际展开字节越界: " + entry.getName());
                }
                output.write(buffer, 0, length);
            }
        }
        if (total != expected) {
            throw invalid("医疗资源包条目实际展开字节与目录事实不一致: " + entry.getName());
        }
        return output.toByteArray();
    }

    private void validateCompatibility(FullPackageManifest.Compatibility compatibility) {
        boolean compatible = properties.supportedPackageFormatVersion()
            .equals(compatibility.packageFormatVersion())
            && versionInRange(
                properties.currentEngineVersion(),
                compatibility.minimumEngineVersion(),
                compatibility.maximumEngineVersion())
            && schemaInRange(
                properties.currentDatabaseSchemaVersion(),
                compatibility.minimumDatabaseSchemaVersion(),
                compatibility.maximumDatabaseSchemaVersion());
        if (!compatible) {
            throw invalid("医疗资源包格式、引擎或数据库模式不兼容当前院内实例");
        }
    }

    private boolean versionInRange(String current, String minimum, String maximum) {
        int[] actual = numericVersion(current, false);
        int[] lower = numericVersion(minimum, false);
        int[] upper = numericVersion(maximum, true);
        return compare(actual, lower) >= 0 && compare(actual, upper) <= 0;
    }

    private int[] numericVersion(String source, boolean upperBound) {
        String normalized = source.split("-", 2)[0];
        String[] parts = normalized.split("\\.");
        int[] result = new int[] {0, 0, 0};
        for (int index = 0; index < result.length; index++) {
            if (index >= parts.length) {
                result[index] = upperBound ? Integer.MAX_VALUE : 0;
            } else if ("x".equals(parts[index])) {
                if (!upperBound) {
                    throw invalid("最低兼容引擎版本不得使用通配符");
                }
                result[index] = Integer.MAX_VALUE;
            } else {
                result[index] = Integer.parseInt(parts[index]);
            }
        }
        return result;
    }

    private int compare(int[] left, int[] right) {
        for (int index = 0; index < left.length; index++) {
            int compared = Integer.compare(left[index], right[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private boolean schemaInRange(String current, String minimum, String maximum) {
        int actual = Integer.parseInt(current.substring(1));
        return actual >= Integer.parseInt(minimum.substring(1))
            && actual <= Integer.parseInt(maximum.substring(1));
    }

    private VersionedAssetType typeFromAssetPath(String path) {
        String[] segments = path.split("/");
        if (segments.length != 4
                || !"assets".equals(segments[0])
                || !segments[3].endsWith(".json")) {
            throw invalid("医疗资源包 manifest 含非标准正文路径: " + path);
        }
        try {
            return VersionedAssetType.valueOf(segments[1]);
        } catch (IllegalArgumentException exception) {
            throw invalid("医疗资源包正文路径含未知资产类型: " + path);
        }
    }

    private String assetPath(PortableAssetDocument document) {
        return "assets/" + document.assetType().name() + "/"
            + document.assetIdentity() + "/" + document.versionId() + ".json";
    }

    private void validateTargetLicense(
            PortableAssetDocument document,
            String targetHospitalId) {
        String exact = "HOSPITAL:" + targetHospitalId;
        for (PortableAssetDocument.License license : document.licenses()) {
            if (!"AUTHORIZED_HOSPITALS".equals(license.redistributionScope())
                    && !exact.equals(license.redistributionScope())) {
                throw invalid("医疗资源包许可不允许交付到目标医院: "
                    + document.assetIdentity());
            }
        }
    }

    private void validateRelease(
            FullPackageManifest manifest,
            FullPackageReleaseDocument release,
            Map<AssetKey, PortableAssetDocument> documents,
            Map<AssetKey, FullPackageManifest.FileEntry> assetFiles) {
        if (!manifest.platformReleaseIdentity().equals(release.platformReleaseIdentity())) {
            throw invalid("医疗资源包 manifest 与平台版本重建文档身份不一致");
        }
        Map<AssetKey, FullPackageReleaseDocument.Entry> releaseEntries = new HashMap<>();
        EnumSet<VersionedAssetType> activeTypes = EnumSet.noneOf(VersionedAssetType.class);
        for (FullPackageReleaseDocument.Entry entry : release.entries()) {
            AssetKey key = new AssetKey(entry.assetType(), entry.assetIdentity());
            releaseEntries.put(key, entry);
            PortableAssetDocument document = documents.get(key);
            if (entry.state() == ReleaseEntryState.DISABLED) {
                if (document != null) {
                    throw invalid("停用资产不得携带活动正文: " + entry.assetIdentity());
                }
                continue;
            }
            FullPackageManifest.FileEntry file = assetFiles.get(key);
            if (document == null || file == null
                    || !entry.versionId().equals(document.versionId())
                    || !entry.versionNo().equals(document.versionNo())
                    || !("sha256:" + document.contentSha256())
                        .equals(entry.sourceContentSha256())
                    || !entry.exportedContentDigest().equals(document.contentDigest())
                    || !entry.assetPath().equals(file.path())) {
                throw invalid("平台版本活动条目未精确绑定包内可恢复正文: "
                    + entry.assetIdentity());
            }
            activeTypes.add(entry.assetType());
        }
        if (!releaseEntries.keySet().containsAll(documents.keySet())
                || documents.size() != assetFiles.size()) {
            throw invalid("医疗资源包含平台版本未声明的孤立正文");
        }
        String actualManifestSha256 = FullPackageReleaseIntegrity.manifestSha256(
            release.entries());
        if (!("sha256:" + actualManifestSha256).equals(release.platformManifestSha256())) {
            throw invalid("平台版本明细 SHA-256 与完整精确条目不一致");
        }
        EnumSet<VersionedAssetType> missing = EnumSet.allOf(VersionedAssetType.class);
        missing.removeAll(activeTypes);
        if (!missing.isEmpty()) {
            throw invalid("完整医疗资源包必须真实包含全部 13 类正文，缺少: " + missing);
        }
        Set<String> activeVersions = new HashSet<>();
        documents.values().forEach(document -> activeVersions.add(document.versionId()));
        for (FullPackageReleaseDocument.Withdrawal withdrawal : release.withdrawals()) {
            FullPackageReleaseDocument.Entry state = releaseEntries.get(
                new AssetKey(withdrawal.assetType(), withdrawal.assetIdentity()));
            if (state == null || state.state() != ReleaseEntryState.DISABLED) {
                throw invalid("撤回事实必须绑定当前明确停用资产: "
                    + withdrawal.assetIdentity());
            }
            if (withdrawal.successorVersionId() != null
                    && !activeVersions.contains(withdrawal.successorVersionId())) {
                throw invalid("撤回替代版本不在当前完整包活动版本中: "
                    + withdrawal.successorVersionId());
            }
        }
    }

    private void validateDependencyClosure(Map<AssetKey, PortableAssetDocument> documents) {
        for (PortableAssetDocument owner : documents.values()) {
            for (PortableAssetDocument.Dependency dependency : owner.dependencies()) {
                PortableAssetDocument target = documents.get(
                    new AssetKey(dependency.assetType(), dependency.assetIdentity()));
                if (target == null
                        || !target.versionId().equals(dependency.versionId())
                        || !target.versionNo().equals(dependency.versionNo())
                        || !target.contentDigest().equals(dependency.contentDigest())) {
                    throw invalid("医疗资源包精确依赖不闭合: "
                        + owner.assetIdentity() + " -> " + dependency.assetIdentity());
                }
            }
        }
    }

    private String digest(FileChannel channel) throws IOException {
        SM3.Digest digest = new SM3.Digest();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        channel.position(0);
        while (channel.read(buffer) != -1) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        return "sm3:" + HexFormat.of().formatHex(digest.digest());
    }

    private void requireCanonicalPath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || path.contains("//")) {
            throw invalid("医疗资源包条目必须使用规范相对路径: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (!PATH_SEGMENT.matcher(segment).matches()) {
                throw invalid("医疗资源包条目路径含越界或非规范片段: " + path);
            }
        }
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw invalid("医疗资源包展开大小溢出安全边界");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private record ArchiveIndex(Map<String, ZipArchiveEntry> entries, long expandedBytes) {
        private ZipArchiveEntry require(String path) {
            ZipArchiveEntry entry = entries.get(path);
            if (entry == null) {
                throw invalid("医疗资源包缺少必需条目: " + path);
            }
            return entry;
        }
    }

    private record AssetKey(VersionedAssetType type, String identity) {
    }
}
