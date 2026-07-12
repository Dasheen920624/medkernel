package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 未信任上传字节进入受管隔离区前的流式大小与路径边界。 */
class FullPackageQuarantineStoreTest {

    @TempDir
    Path temporaryDirectory;

    private final SmCryptoService crypto = new SmCryptoService();

    @Test
    void streamsBytesToContentAddressedManagedFile() throws Exception {
        Path root = temporaryDirectory.resolve("quarantine");
        FullPackageQuarantineStore store = new FullPackageQuarantineStore(root, 1024, crypto);
        byte[] bytes = "real-mkp-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        QuarantinedFullPackage stored = store.ingest(new ByteArrayInputStream(bytes));

        assertThat(stored.packageFileSize()).isEqualTo(bytes.length);
        assertThat(stored.packageFileDigest()).matches("sm3:[0-9a-f]{64}");
        assertThat(stored.quarantineCoordinate())
            .isEqualTo("objects/" + stored.packageFileDigest().substring(4, 6)
                + "/" + stored.packageFileDigest().substring(4) + ".mkp");
        assertThat(stored.path()).startsWith(root.toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(bytes);
    }

    @Test
    void rejectsOversizedStreamWithoutPublishingPartialObject() throws Exception {
        Path root = temporaryDirectory.resolve("quarantine");
        FullPackageQuarantineStore store = new FullPackageQuarantineStore(root, 4, crypto);

        assertThatThrownBy(() -> store.ingest(new ByteArrayInputStream(new byte[5])))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(exception).hasMessageContaining("大小上限");
            });

        Path incoming = root.resolve("incoming");
        assertThat(Files.exists(incoming) ? Files.list(incoming).toList() : java.util.List.of())
            .isEmpty();
        assertThat(Files.exists(root.resolve("objects"))).isFalse();
    }

    @Test
    void rejectsConfiguredRootWhenItIsSymbolicLink() throws Exception {
        Path realRoot = Files.createDirectory(temporaryDirectory.resolve("real-quarantine"));
        Path linkedRoot = temporaryDirectory.resolve("linked-quarantine");
        Files.createSymbolicLink(linkedRoot, realRoot);
        FullPackageQuarantineStore store = new FullPackageQuarantineStore(linkedRoot, 1024, crypto);

        assertThatThrownBy(() -> store.ingest(new ByteArrayInputStream(new byte[]{1})))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(exception).hasMessageContaining("符号链接");
            });

        assertThat(Files.list(realRoot).toList()).isEmpty();
    }

    @Test
    void concurrentIdenticalUploadsConvergeWithoutLeavingPartialFiles() throws Exception {
        int concurrency = 16;
        Path root = temporaryDirectory.resolve("quarantine");
        FullPackageQuarantineStore store = new FullPackageQuarantineStore(root, 1024, crypto);
        byte[] bytes = "same-real-mkp-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CyclicBarrier completedReads = new CyclicBarrier(concurrency);

        List<Future<QuarantinedFullPackage>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            for (int index = 0; index < concurrency; index++) {
                futures.add(executor.submit(() -> store.ingest(
                    new BarrierAtEndInputStream(bytes, completedReads))));
            }
        }

        List<QuarantinedFullPackage> packages = new ArrayList<>();
        for (Future<QuarantinedFullPackage> future : futures) {
            packages.add(future.get());
        }
        assertThat(packages)
            .extracting(QuarantinedFullPackage::quarantineCoordinate)
            .containsOnly(packages.getFirst().quarantineCoordinate());
        try (var partials = Files.list(root.resolve("incoming"))) {
            assertThat(partials.toList()).isEmpty();
        }
    }

    @Test
    void resolvesOnlyTheExactPreviouslyIngestedContentAddressedArtifact() throws Exception {
        Path root = temporaryDirectory.resolve("quarantine");
        FullPackageQuarantineStore store = new FullPackageQuarantineStore(root, 1024, crypto);
        QuarantinedFullPackage stored = store.ingest(
            new ByteArrayInputStream("exact-package".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(store.resolve(
            stored.quarantineCoordinate(),
            stored.packageFileDigest(),
            stored.packageFileSize())).isEqualTo(stored);
        assertThatThrownBy(() -> store.resolve(
            "../escape.mkp", stored.packageFileDigest(), stored.packageFileSize()))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.resolve(
            stored.quarantineCoordinate(), "sm3:" + "0".repeat(64), stored.packageFileSize()))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.resolve(
            stored.quarantineCoordinate(), stored.packageFileDigest(), stored.packageFileSize() + 1))
            .isInstanceOf(ApiException.class);
    }

    private static final class BarrierAtEndInputStream extends InputStream {

        private final byte[] bytes;
        private final CyclicBarrier completedReads;
        private boolean delivered;

        private BarrierAtEndInputStream(byte[] bytes, CyclicBarrier completedReads) {
            this.bytes = bytes.clone();
            this.completedReads = completedReads;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException("测试只使用批量读取");
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (!delivered) {
                delivered = true;
                System.arraycopy(bytes, 0, target, offset, bytes.length);
                return bytes.length;
            }
            try {
                completedReads.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("等待并发上传被中断", exception);
            } catch (BrokenBarrierException exception) {
                throw new IOException("并发上传屏障失效", exception);
            }
            return -1;
        }
    }
}
