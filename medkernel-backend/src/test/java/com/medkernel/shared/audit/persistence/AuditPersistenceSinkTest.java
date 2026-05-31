package com.medkernel.shared.audit.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Sink 失败兜底测试。
 *
 * <p>当 {@link AuditChainWriter#persist} 抛错时：
 * <ul>
 *   <li>异常被吞，不向调用方传播（业务主链路不受影响）</li>
 *   <li>{@code medkernel_audit_persistence_failures_total} 计数器递增</li>
 *   <li>验签成功计数器 {@code medkernel_audit_chain_signed_total} 不增长</li>
 * </ul>
 */
class AuditPersistenceSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void persistenceFailureIsSwallowedCountsAndWritesFallbackFile() throws Exception {
        AuditChainWriter writer = mock(AuditChainWriter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);
        metrics.register();
        AuditFallbackStore fallbackStore = new AuditFallbackStore(
            tempDir.resolve("audit-fallback.jsonl"),
            new com.fasterxml.jackson.databind.ObjectMapper());

        doThrow(new DataAccessResourceFailureException("db down"))
            .when(writer).persist(org.mockito.ArgumentMatchers.any(AuditEvent.class));

        AuditPersistenceSink sink = new AuditPersistenceSink(writer, metrics, fallbackStore, Runnable::run);

        AuditEvent event = AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "test");

        assertThatCode(() -> sink.onNoTransaction(event)).doesNotThrowAnyException();

        verify(writer, times(1)).persist(event);
        assertThat(registry.counter("medkernel_audit_persistence_failures_total").count())
            .isEqualTo(1.0);
        assertThat(registry.counter("medkernel_audit_chain_signed_total").count())
            .isEqualTo(0.0);
        assertThat(registry.counter("medkernel_audit_fallback_written_total").count())
            .isEqualTo(1.0);

        String fallback = Files.readString(tempDir.resolve("audit-fallback.jsonl"));
        assertThat(fallback)
            .contains(event.id())
            .contains("db down")
            .contains("\"action\":\"CREATE\"");
    }

    @Test
    void successfulPersistenceIncrementsSignedCounter() {
        AuditChainWriter writer = mock(AuditChainWriter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);
        metrics.register();
        AuditFallbackStore fallbackStore = new AuditFallbackStore(
            tempDir.resolve("audit-fallback.jsonl"),
            new com.fasterxml.jackson.databind.ObjectMapper());

        // mocked writer returns null by default — sink only inspects metrics
        AuditPersistenceSink sink = new AuditPersistenceSink(writer, metrics, fallbackStore, Runnable::run);
        AuditEvent event = AuditEvent.of(AuditAction.PUBLISH, "rule", "r-2", "test");
        sink.onNoTransaction(event);

        assertThat(registry.counter("medkernel_audit_chain_signed_total").count())
            .isEqualTo(1.0);
        assertThat(registry.counter("medkernel_audit_persistence_failures_total").count())
            .isEqualTo(0.0);
    }

    @Test
    void afterCommitSchedulesPersistenceOnExecutor() {
        AuditChainWriter writer = mock(AuditChainWriter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);
        metrics.register();
        Queue<Runnable> queue = new ArrayDeque<>();
        AuditFallbackStore fallbackStore = new AuditFallbackStore(
            tempDir.resolve("audit-fallback.jsonl"),
            new com.fasterxml.jackson.databind.ObjectMapper());

        AuditPersistenceSink sink = new AuditPersistenceSink(writer, metrics, fallbackStore, queue::add);
        AuditEvent event = AuditEvent.of(AuditAction.PUBLISH, "rule", "r-3", "test");

        sink.onAfterCommit(event);
        verify(writer, times(0)).persist(event);

        assertThat(queue).hasSize(1);
        queue.remove().run();
        verify(writer, times(1)).persist(event);
        assertThat(registry.counter("medkernel_audit_chain_signed_total").count())
            .isEqualTo(1.0);
    }

    @Test
    void afterCommitPersistsFailedEventSynchronouslyForFailureEvidence() {
        AuditChainWriter writer = mock(AuditChainWriter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);
        metrics.register();
        Queue<Runnable> queue = new ArrayDeque<>();
        AuditFallbackStore fallbackStore = new AuditFallbackStore(
            tempDir.resolve("audit-fallback.jsonl"),
            new com.fasterxml.jackson.databind.ObjectMapper());

        AuditPersistenceSink sink = new AuditPersistenceSink(writer, metrics, fallbackStore, queue::add);
        AuditEvent event = AuditEvent.failure(
            AuditAction.EXECUTE, "context_snapshot", "ctx-1", "ENG-CONTEXT-003", "test");

        sink.onAfterCommit(event);

        assertThat(queue).isEmpty();
        verify(writer, times(1)).persist(event);
        assertThat(registry.counter("medkernel_audit_chain_signed_total").count())
            .isEqualTo(1.0);
    }
}
