package com.medkernel.engine.knowledge.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.engine.knowledge.authority.PackageSigningStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 真实 {@code .mkp} 受管文件存储。
 *
 * <p>容器固定使用按路径排序、无压缩、固定 DOS 时间的 ZIP；写入完成后先逐条重读 manifest、
 * 签名信封和全部内容字节，再原子进入最终坐标。下载时以注册表中的整文件摘要和大小重新校验。
 */
@Service
public class FullPackageArtifactStore {

    private static final String MANIFEST_PATH = "manifest.json";
    private static final String SIGNATURE_PATH = "signature.json";
    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final int MAX_CONTROL_FILE_BYTES = 8 * 1024 * 1024;

    private final Path root;
    private final FullPackageManifestCodec manifests;
    private final PackageSignatureEnvelopeCodec signatures;
    private final SmCryptoService crypto;

    @Autowired
    public FullPackageArtifactStore(
            FullPackageStorageProperties properties,
            FullPackageManifestCodec manifests,
            PackageSignatureEnvelopeCodec signatures,
            SmCryptoService crypto) {
        this(Path.of(properties.root()), manifests, signatures, crypto);
    }

    FullPackageArtifactStore(
            Path root,
            FullPackageManifestCodec manifests,
            PackageSignatureEnvelopeCodec signatures,
            SmCryptoService crypto) {
        this.root = root.toAbsolutePath().normalize();
        this.manifests = manifests;
        this.signatures = signatures;
        this.crypto = crypto;
    }

    /**
     * 写入完整包并在登记前从真实文件逐条重读。
     *
     * @param manifestBytes 规范 manifest 字节
     * @param signatureBytes 规范公开签名信封字节
     * @param contentFiles manifest 声明的全部内容文件
     */
    public StoredFullPackage store(
            byte[] manifestBytes,
            byte[] signatureBytes,
            List<PortableAssetFile> contentFiles) {
        FullPackageManifest manifest = manifests.decode(manifestBytes);
        PackageSignatureEnvelope envelope = signatures.decode(signatureBytes);
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        validateEnvelopeBinding(manifest, envelope, manifestDigest);
        Map<String, byte[]> content = validateContent(manifest, contentFiles);
        Map<String, byte[]> expected = new TreeMap<>(content);
        expected.put(MANIFEST_PATH, manifestBytes.clone());
        Map<String, byte[]> entries = new TreeMap<>(expected);
        entries.put(SIGNATURE_PATH, signatureBytes.clone());

        String coordinate = coordinate(manifest, manifestDigest);
        Path target = resolveCoordinate(coordinate);
        try {
            prepareDeliveryDirectory(target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyContainer(target, expected, signatureBytes, manifestDigest);
                return facts(target, coordinate);
            }
            Path temporary = Files.createTempFile(target.getParent(), ".mkp-", ".part");
            try {
                writeContainer(temporary, entries);
                forceFile(temporary);
                verifyContainer(temporary, expected, signatureBytes, manifestDigest);
                moveAtomically(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            verifyContainer(target, expected, signatureBytes, manifestDigest);
            return facts(target, coordinate);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "完整医疗资源包受管目录无法写入或重读",
                exception);
        }
    }

    /**
     * 恢复已签名但因数据库事务回滚尚未登记的真实文件。
     *
     * <p>只有 manifest 和全部正文与本次快照逐字节一致、容器仍为确定性布局、公开签名信封
     * 精确绑定 manifest 时才返回；签名密码学验证仍由上层使用包外固定根完成。
     */
    public Optional<RecoveredFullPackage> recoverExisting(
            byte[] manifestBytes,
            List<PortableAssetFile> contentFiles) {
        FullPackageManifest manifest = manifests.decode(manifestBytes);
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        Map<String, byte[]> expected = new TreeMap<>(validateContent(manifest, contentFiles));
        expected.put(MANIFEST_PATH, manifestBytes.clone());
        String coordinate = coordinate(manifest, manifestDigest);
        Path target = resolveCoordinate(coordinate);
        if (!managedDeliveryDirectoryExists(target)) {
            return Optional.empty();
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            PackageSignatureEnvelope envelope =
                verifyContainer(target, expected, null, manifestDigest);
            return Optional.of(new RecoveredFullPackage(
                envelope,
                facts(target, coordinate)));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "既有完整医疗资源包无法从受管目录严格重读",
                exception);
        }
    }

    /**
     * 按不可变注册事实打开下载流。摘要计算与返回流使用同一文件描述符，避免校验后换文件。
     */
    public InputStream openVerified(PackageRegistration registration) {
        if (registration == null
                || registration.signingStatus() != PackageSigningStatus.SIGNED
                || registration.packageFileSize() <= 0
                || registration.packageFileDigest() == null) {
            throw conflict("医疗资源包缺少可下载的不可变注册文件事实");
        }
        Path path = resolveCoordinate(registration.storageCoordinate());
        requireManagedDeliveryDirectory(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw conflict("已登记医疗资源包文件不存在或不是受管普通文件");
        }
        FileChannel channel = null;
        try {
            channel = FileChannel.open(path, StandardOpenOption.READ);
            if (channel.size() != registration.packageFileSize()) {
                throw conflict("已登记医疗资源包实际大小与注册表不一致");
            }
            String actualDigest = "sm3:" + crypto.sm3Hex(Channels.newInputStream(channel));
            if (!actualDigest.equals(registration.packageFileDigest())) {
                throw conflict("已登记医疗资源包实际字节摘要与注册表不一致");
            }
            channel.position(0);
            return Channels.newInputStream(channel);
        } catch (ApiException exception) {
            closeQuietly(channel);
            throw exception;
        } catch (IOException exception) {
            closeQuietly(channel);
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "已登记医疗资源包文件无法读取",
                exception);
        }
    }

    private Map<String, byte[]> validateContent(
            FullPackageManifest manifest,
            List<PortableAssetFile> contentFiles) {
        if (contentFiles == null) {
            throw invalid("完整医疗资源包内容文件列表不能为空");
        }
        Map<String, PortableAssetFile> supplied = new HashMap<>();
        for (PortableAssetFile file : contentFiles) {
            if (file == null || file.path() == null
                    || MANIFEST_PATH.equals(file.path())
                    || SIGNATURE_PATH.equals(file.path())
                    || supplied.putIfAbsent(file.path(), file) != null) {
                throw invalid("完整医疗资源包内容文件缺失、重复或占用保留路径");
            }
        }
        Map<String, byte[]> result = new TreeMap<>();
        for (FullPackageManifest.FileEntry declared : manifest.files()) {
            PortableAssetFile file = supplied.remove(declared.path());
            if (file == null || file.bytes() == null || file.bytes().length == 0) {
                throw invalid("manifest 声明的内容文件缺失: " + declared.path());
            }
            byte[] bytes = file.bytes();
            String digest = digest(bytes);
            if (declared.size() != bytes.length
                    || !declared.sm3Digest().equals(digest)
                    || !declared.sm3Digest().equals(file.sm3Digest())) {
                throw invalid("manifest 内容文件大小或摘要不一致: " + declared.path());
            }
            result.put(declared.path(), bytes);
        }
        if (!supplied.isEmpty()) {
            throw invalid("完整医疗资源包包含 manifest 未声明的内容文件: " + supplied.keySet());
        }
        return result;
    }

    private void validateEnvelopeBinding(
            FullPackageManifest manifest,
            PackageSignatureEnvelope envelope,
            String manifestDigest) {
        if (!manifest.authorityId().equals(envelope.authorityId())
                || !manifest.issuerInstanceId().equals(envelope.issuerInstanceId())
                || !manifest.keyId().equals(envelope.keyId())
                || manifest.releaseSequence() != envelope.releaseSequence()
                || !manifestDigest.equals(envelope.manifestDigest())) {
            throw invalid("公开签名信封未精确绑定当前 FULL manifest");
        }
    }

    private void writeContainer(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(
                    path,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING),
                StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                CRC32 crc = new CRC32();
                crc.update(entry.getValue());
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(entry.getValue().length);
                zipEntry.setCompressedSize(entry.getValue().length);
                zipEntry.setCrc(crc.getValue());
                zipEntry.setTimeLocal(ZIP_TIME);
                output.putNextEntry(zipEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private PackageSignatureEnvelope verifyContainer(
            Path path,
            Map<String, byte[]> expected,
            byte[] expectedSignature,
            String expectedManifestDigest) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw conflict("完整医疗资源包不是受管普通文件");
        }
        Set<String> expectedNames = new java.util.TreeSet<>(expected.keySet());
        expectedNames.add(SIGNATURE_PATH);
        List<String> orderedNames = List.copyOf(expectedNames);
        int index = 0;
        byte[] actualSignature = null;
        try (ZipInputStream input = new ZipInputStream(
                Files.newInputStream(path, StandardOpenOption.READ),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (index >= orderedNames.size()
                        || !orderedNames.get(index).equals(name)
                        || entry.isDirectory()
                        || entry.getMethod() != ZipEntry.STORED
                        || !ZIP_TIME.equals(entry.getTimeLocal())) {
                    throw conflict("完整医疗资源包出现未声明、重复或非确定性容器条目: " + name);
                }
                if (SIGNATURE_PATH.equals(name)) {
                    actualSignature = expectedSignature == null
                        ? readBoundedEntry(input, MAX_CONTROL_FILE_BYTES, name)
                        : readEntry(input, expectedSignature.length, name);
                    if (expectedSignature != null
                            && !Arrays.equals(actualSignature, expectedSignature)) {
                        throw conflict("完整医疗资源包落盘重读签名信封不一致");
                    }
                } else {
                    byte[] expectedBytes = expected.get(name);
                    byte[] actual = readEntry(input, expectedBytes.length, name);
                    if (!Arrays.equals(actual, expectedBytes)) {
                        throw conflict("完整医疗资源包落盘重读字节不一致: " + name);
                    }
                }
                index++;
                input.closeEntry();
            }
        }
        if (index != orderedNames.size() || actualSignature == null) {
            throw conflict("完整医疗资源包落盘后缺少 manifest 声明或签名条目");
        }
        byte[] manifestBytes = expected.get(MANIFEST_PATH);
        if (!expectedManifestDigest.equals(manifests.sm3Digest(manifestBytes))) {
            throw conflict("完整医疗资源包落盘后 manifest 摘要不一致");
        }
        FullPackageManifest manifest = manifests.decode(manifestBytes);
        PackageSignatureEnvelope envelope = signatures.decode(actualSignature);
        validateEnvelopeBinding(manifest, envelope, expectedManifestDigest);
        return envelope;
    }

    private byte[] readEntry(InputStream input, int expectedSize, String name) throws IOException {
        if (expectedSize > MAX_CONTROL_FILE_BYTES
                && (MANIFEST_PATH.equals(name) || SIGNATURE_PATH.equals(name))) {
            throw conflict("完整医疗资源包控制文件超过安全上限: " + name);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedSize);
        byte[] buffer = new byte[8192];
        int total = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > expectedSize) {
                throw conflict("完整医疗资源包条目实际大小超过声明: " + name);
            }
            output.write(buffer, 0, length);
        }
        if (total != expectedSize) {
            throw conflict("完整医疗资源包条目实际大小与声明不一致: " + name);
        }
        return output.toByteArray();
    }

    private byte[] readBoundedEntry(
            InputStream input,
            int maximumSize,
            String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > maximumSize) {
                throw conflict("完整医疗资源包控制文件超过安全上限: " + name);
            }
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private StoredFullPackage facts(Path path, String coordinate) throws IOException {
        long size = Files.size(path);
        String packageDigest;
        try (InputStream input = Files.newInputStream(path)) {
            packageDigest = "sm3:" + crypto.sm3Hex(input);
        }
        return new StoredFullPackage(path, coordinate, packageDigest, size);
    }

    private Path resolveCoordinate(String coordinate) {
        if (coordinate == null
                || coordinate.startsWith("/")
                || coordinate.contains("\\")
                || !coordinate.matches(
                    "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}/[0-9a-f]{64}\\.mkp")) {
            throw invalid("医疗资源包受管存储坐标不规范");
        }
        Path resolved = root.resolve(coordinate).normalize();
        if (!resolved.startsWith(root)) {
            throw invalid("医疗资源包受管存储坐标越界");
        }
        return resolved;
    }

    private void prepareDeliveryDirectory(Path target) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("医疗资源包受管根必须是真实目录，不能是符号链接");
        }
        Path directory = target.getParent();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(directory);
            } catch (FileAlreadyExistsException ignored) {
                // 并发创建后仍由下方无跟随校验裁定，不能把符号链接当作成功目录。
            }
        }
        requireManagedDeliveryDirectory(target);
    }

    private boolean managedDeliveryDirectoryExists(Path target) {
        Path directory = target.getParent();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        requireManagedDeliveryDirectory(target);
        return true;
    }

    private void requireManagedDeliveryDirectory(Path target) {
        Path directory = target.getParent();
        if (directory == null
                || !root.equals(directory.getParent())
                || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("医疗资源包交付目录必须是受管根下的真实单层目录");
        }
    }

    private String coordinate(FullPackageManifest manifest, String manifestDigest) {
        return manifest.deliveryId() + "/"
            + manifestDigest.substring("sm3:".length()) + ".mkp";
    }

    private void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("医疗资源包存储文件系统不支持原子提交", exception);
        }
    }

    private String digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    private void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // 原始读取失败优先返回，关闭失败不覆盖真实根因。
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
