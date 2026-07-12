package com.medkernel.engine.knowledge.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.regex.Pattern;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.bouncycastle.jcajce.provider.digest.SM3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 把未信任上传流按大小上限写入内容寻址的受管隔离区。 */
@Service
public class FullPackageQuarantineStore {

    private static final Pattern SM3 = Pattern.compile("sm3:[0-9a-f]{64}");

    private final Path root;
    private final long maximumPackageBytes;
    private final SmCryptoService crypto;

    @Autowired
    public FullPackageQuarantineStore(
            FullPackageImportProperties properties,
            SmCryptoService crypto) {
        this(Path.of(properties.quarantineRoot()), properties.maxPackageBytes(), crypto);
    }

    FullPackageQuarantineStore(Path root, long maximumPackageBytes, SmCryptoService crypto) {
        if (root == null || maximumPackageBytes <= 0 || crypto == null) {
            throw new IllegalArgumentException("隔离根、文件上限和国密实现不能为空");
        }
        this.root = root.toAbsolutePath().normalize();
        this.maximumPackageBytes = maximumPackageBytes;
        this.crypto = crypto;
    }

    /**
     * 流式接收一个真实文件；只有完整写入、强制落盘并重读一致后才进入最终隔离坐标。
     */
    public QuarantinedFullPackage ingest(InputStream source) {
        if (source == null) {
            throw invalid("必须提交真实医疗资源包文件流");
        }
        Path temporary = null;
        try {
            prepareDirectory(root, root);
            Path incoming = prepareDirectory(root.resolve("incoming"), root);
            temporary = Files.createTempFile(incoming, ".upload-", ".part");
            StreamFacts facts = writeBounded(source, temporary);
            if (facts.size() == 0) {
                throw invalid("医疗资源包文件不能为空");
            }
            String hex = facts.digest().substring("sm3:".length());
            Path objects = prepareDirectory(root.resolve("objects"), root);
            Path shard = prepareDirectory(objects.resolve(hex.substring(0, 2)), objects);
            String coordinate = "objects/" + hex.substring(0, 2) + "/" + hex + ".mkp";
            Path target = shard.resolve(hex + ".mkp").normalize();
            if (!target.startsWith(root)) {
                throw invalid("医疗资源包隔离坐标越界");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyExisting(target, facts);
            } else {
                if (moveAtomically(temporary, target)) {
                    temporary = null;
                }
                verifyExisting(target, facts);
            }
            return new QuarantinedFullPackage(
                target,
                coordinate,
                facts.digest(),
                facts.size());
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "医疗资源包隔离目录无法写入、落盘或重读",
                exception);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /**
     * 按预检账本中的坐标、摘要和大小重新打开同一隔离对象；不接受任意宿主路径。
     */
    public QuarantinedFullPackage resolve(
            String quarantineCoordinate,
            String packageFileDigest,
            long packageFileSize) {
        if (quarantineCoordinate == null
                || packageFileDigest == null
                || !SM3.matcher(packageFileDigest).matches()
                || packageFileSize <= 0) {
            throw invalid("医疗资源包隔离回读事实不规范");
        }
        String hex = packageFileDigest.substring("sm3:".length());
        String expectedCoordinate = "objects/" + hex.substring(0, 2)
            + "/" + hex + ".mkp";
        if (!expectedCoordinate.equals(quarantineCoordinate)) {
            throw invalid("医疗资源包隔离坐标未由整包摘要唯一派生");
        }
        Path objects = root.resolve("objects").normalize();
        Path shard = objects.resolve(hex.substring(0, 2)).normalize();
        Path target = root.resolve(quarantineCoordinate).normalize();
        if (!target.startsWith(root)
                || !target.equals(shard.resolve(hex + ".mkp").normalize())) {
            throw invalid("医疗资源包隔离回读坐标越界");
        }
        try {
            requireManagedDirectory(root);
            requireManagedDirectory(objects);
            requireManagedDirectory(shard);
            verifyExisting(target, new StreamFacts(packageFileDigest, packageFileSize));
            return new QuarantinedFullPackage(
                target, quarantineCoordinate, packageFileDigest, packageFileSize);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "医疗资源包隔离对象无法安全回读",
                exception);
        }
    }

    private StreamFacts writeBounded(InputStream source, Path target) throws IOException {
        SM3.Digest digest = new SM3.Digest();
        long total = 0;
        byte[] bytes = new byte[8192];
        try (FileChannel output = FileChannel.open(
                target,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            int length;
            while ((length = source.read(bytes)) != -1) {
                total += length;
                if (total > maximumPackageBytes) {
                    throw invalid("医疗资源包文件超过配置的大小上限");
                }
                digest.update(bytes, 0, length);
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
            }
            output.force(true);
        }
        return new StreamFacts(
            "sm3:" + HexFormat.of().formatHex(digest.digest()),
            total);
    }

    private Path prepareDirectory(Path directory, Path expectedParent) throws IOException {
        if (directory.equals(root)) {
            Files.createDirectories(directory);
        } else if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(directory);
            } catch (FileAlreadyExistsException ignored) {
                // 并发创建后仍由无跟随校验裁定，不能把符号链接视为目录。
            }
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || (!directory.equals(root) && !expectedParent.equals(directory.getParent()))) {
            throw invalid("医疗资源包受管隔离目录不能是符号链接或越界目录");
        }
        return directory;
    }

    private void requireManagedDirectory(Path directory) {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("医疗资源包受管隔离目录不存在或已变为符号链接");
        }
    }

    private void verifyExisting(Path path, StreamFacts expected) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || Files.size(path) != expected.size()) {
            throw conflict("隔离区同摘要坐标已存在不一致文件");
        }
        String actual;
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            actual = "sm3:" + crypto.sm3Hex(input);
        }
        if (!expected.digest().equals(actual)) {
            throw conflict("隔离区同摘要坐标文件重读校验失败");
        }
    }

    private boolean moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("医疗资源包隔离文件系统不支持原子提交", exception);
        } catch (FileAlreadyExistsException exception) {
            // 并发上传相同字节时由调用方重读同一内容寻址目标。
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 主错误优先；遗留 .part 仍位于隔离区，不会成为可预检对象。
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private record StreamFacts(String digest, long size) {
    }
}
