package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.shared.context.OrgScope;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 临床事件引擎派发器的时延预算与硬超时测试。
 */
class ClinicalEventEngineDispatcherTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

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
            "pkg-2026.06",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            "HIS:patient-view",
            "trace-1",
            json.createObjectNode(),
            List.of());
    }
}
