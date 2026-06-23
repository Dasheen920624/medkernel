package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 临床事件引擎派发器的时延预算与硬超时测试。
 */
class ClinicalEventEngineDispatcherTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void dispatchRestoresClinicalEventTraceAndOrgScopeForRuntimeResolution() {
        AtomicReference<String> observedTrace = new AtomicReference<>();
        AtomicReference<OrgScope> observedScope = new AtomicReference<>();
        ClinicalEventEngineAdapter capturingRule = new ClinicalEventEngineAdapter() {
            @Override
            public ClinicalEventEngine engine() {
                return ClinicalEventEngine.RULE;
            }

            @Override
            public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
                observedTrace.set(RequestContext.currentTraceId());
                observedScope.set(RequestContext.currentOrgScope());
                return ClinicalEventEngineDispatchResult.dispatched(engine(), "rule-ok", "已按事件组织解析");
            }
        };
        ClinicalEventEngineDispatcher dispatcher = new ClinicalEventEngineDispatcher(
            List.of(capturingRule, fast(ClinicalEventEngine.PATHWAY), fast(ClinicalEventEngine.CDSS)),
            new SimpleAsyncTaskExecutor("clinical-dispatch-context-test-"),
            new ClinicalEventProperties(1024, Duration.ofSeconds(1), 10, 3, List.of(1L)));
        RequestContext.restore(new RequestContext.Snapshot(
            "ambient-trace", OrgScope.tenant("tenant-A"), "doctor-ambient"));

        List<ClinicalEventEngineDispatchResult> results = dispatcher.dispatch(context());

        assertThat(results).hasSize(3);
        assertThat(observedTrace).hasValue("trace-1");
        assertThat(observedScope.get().departmentId()).isEqualTo("dept-A");
        assertThat(observedScope.get().specialtyId()).isEqualTo("specialty-A");
    }

    @Test
    void dispatchReturnsUnavailableWhenEngineExceedsSyncBudget() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        ClinicalEventEngineAdapter slowRule = new ClinicalEventEngineAdapter() {
            @Override
            public ClinicalEventEngine engine() {
                return ClinicalEventEngine.RULE;
            }

            @Override
            public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return ClinicalEventEngineDispatchResult.dispatched(engine(), "late", "不应等待慢任务完成");
            }
        };
        ClinicalEventEngineDispatcher dispatcher = new ClinicalEventEngineDispatcher(
            List.of(slowRule, fast(ClinicalEventEngine.PATHWAY), fast(ClinicalEventEngine.CDSS)),
            new SimpleAsyncTaskExecutor("clinical-dispatch-test-"),
            new ClinicalEventProperties(1024, Duration.ofMillis(30), 10, 3, List.of(1L)));

        List<ClinicalEventEngineDispatchResult> results = dispatcher.dispatch(context());

        assertThat(results).hasSize(3);
        assertThat(results.getFirst().engine()).isEqualTo(ClinicalEventEngine.RULE);
        assertThat(results.getFirst().status()).isEqualTo(ClinicalEventEngineDispatchStatus.UNAVAILABLE);
        assertThat(results.getFirst().message()).contains("超时");
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void dispatchUsesCurrentThreadWhenTransactionIsActiveSoNewSnapshotsStayVisible() {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> observedThread = new AtomicReference<>();
        ClinicalEventEngineAdapter capturingCdss = new ClinicalEventEngineAdapter() {
            @Override
            public ClinicalEventEngine engine() {
                return ClinicalEventEngine.CDSS;
            }

            @Override
            public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
                observedThread.set(Thread.currentThread());
                return ClinicalEventEngineDispatchResult.dispatched(engine(), "cdss-ok", "已在事务内求值");
            }
        };
        ClinicalEventEngineDispatcher dispatcher = new ClinicalEventEngineDispatcher(
            List.of(fast(ClinicalEventEngine.RULE), fast(ClinicalEventEngine.PATHWAY), capturingCdss),
            new SimpleAsyncTaskExecutor("clinical-dispatch-transaction-test-"),
            new ClinicalEventProperties(1024, Duration.ofSeconds(1), 10, 3, List.of(1L)));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        List<ClinicalEventEngineDispatchResult> results = dispatcher.dispatch(context());

        assertThat(results).hasSize(3);
        assertThat(observedThread).hasValue(callerThread);
    }

    @Test
    void dispatchIsolatesUnavailableEngineInNestedTransactionWhenCallerTransactionIsActive() {
        RecordingTransactionManager txManager = new RecordingTransactionManager();
        ClinicalEventEngineAdapter unavailableRule = new ClinicalEventEngineAdapter() {
            @Override
            public ClinicalEventEngine engine() {
                return ClinicalEventEngine.RULE;
            }

            @Override
            public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
                throw new IllegalStateException("规则引擎数据库写入失败");
            }
        };
        ClinicalEventEngineDispatcher dispatcher = new ClinicalEventEngineDispatcher(
            List.of(unavailableRule, fast(ClinicalEventEngine.PATHWAY), fast(ClinicalEventEngine.CDSS)),
            new SimpleAsyncTaskExecutor("clinical-dispatch-nested-test-"),
            new ClinicalEventProperties(1024, Duration.ofSeconds(1), 10, 3, List.of(1L)),
            txManager);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        List<ClinicalEventEngineDispatchResult> results = dispatcher.dispatch(context());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).engine()).isEqualTo(ClinicalEventEngine.RULE);
        assertThat(results.get(0).status()).isEqualTo(ClinicalEventEngineDispatchStatus.UNAVAILABLE);
        assertThat(results.get(0).message()).contains("规则引擎数据库写入失败");
        assertThat(results.get(1).status()).isEqualTo(ClinicalEventEngineDispatchStatus.DISPATCHED);
        assertThat(results.get(2).status()).isEqualTo(ClinicalEventEngineDispatchStatus.DISPATCHED);
        assertThat(txManager.propagationBehaviors())
            .containsExactly(
                TransactionDefinition.PROPAGATION_NESTED,
                TransactionDefinition.PROPAGATION_NESTED,
                TransactionDefinition.PROPAGATION_NESTED);
    }

    private ClinicalEventEngineAdapter fast(ClinicalEventEngine engine) {
        return new ClinicalEventEngineAdapter() {
            @Override
            public ClinicalEventEngine engine() {
                return engine;
            }

            @Override
            public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
                return ClinicalEventEngineDispatchResult.dispatched(engine, "ok-" + engine.name(), "已处理");
            }
        };
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<Integer> propagationBehaviors = new java.util.ArrayList<>();

        private RecordingTransactionManager() {
            setNestedTransactionAllowed(true);
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            propagationBehaviors.add(definition.getPropagationBehavior());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
        }

        private List<Integer> propagationBehaviors() {
            return List.copyOf(propagationBehaviors);
        }
    }

    private ClinicalEventContext context() {
        return new ClinicalEventContext(
            "evt-1",
            "tenant-A",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW,
            "MPI-1",
            "ENC-1",
            ClinicalSetting.INPATIENT,
            "ctx-1",
            "HIS",
            "runtime-release-test",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            "HIS:patient-view",
            "trace-1",
            ClinicalEventTestContexts.resources("MPI-1", "HIS", "TERM-2026.06",
                Instant.parse("2026-06-01T01:00:00Z")),
            json.createObjectNode(),
            List.of());
    }
}
