package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.SimpleTriggerContext;

import com.medkernel.shared.config.SystemConfigService;

class ClinicalEventOutboxSchedulerTest {

    @Test
    void registersDynamicTaskAndReadsLatestIntervalForEveryExecution() {
        ClinicalEventOutboxWorker worker = mock(ClinicalEventOutboxWorker.class);
        ClinicalEventProperties properties = properties(true);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.runtimeClinicalEventWorkerPollIntervalMs(properties)).thenReturn(45_000L);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new ClinicalEventOutboxScheduler(worker, properties, configService).configureTasks(registrar);

        List<TriggerTask> tasks = registrar.getTriggerTaskList();
        assertThat(tasks).hasSize(1);
        tasks.getFirst().getRunnable().run();
        verify(worker).pollOnce();

        Instant completedAt = Instant.parse("2026-06-15T00:00:00Z");
        SimpleTriggerContext context = new SimpleTriggerContext(
            completedAt.minusSeconds(2),
            completedAt.minusSeconds(1),
            completedAt);
        assertThat(tasks.getFirst().getTrigger().nextExecution(context))
            .isEqualTo(completedAt.plusMillis(45_000L));
        verify(configService).runtimeClinicalEventWorkerPollIntervalMs(properties);
    }

    @Test
    void doesNotRegisterWorkerWhenDisabled() {
        ClinicalEventOutboxWorker worker = mock(ClinicalEventOutboxWorker.class);
        ClinicalEventProperties properties = properties(false);
        SystemConfigService configService = mock(SystemConfigService.class);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new ClinicalEventOutboxScheduler(worker, properties, configService).configureTasks(registrar);

        assertThat(registrar.getTriggerTaskList()).isEmpty();
        verifyNoInteractions(worker, configService);
    }

    private ClinicalEventProperties properties(boolean workerEnabled) {
        return new ClinicalEventProperties(
            1024,
            Duration.ofMillis(50),
            10,
            3,
            List.of(1L, 5L),
            200L,
            workerEnabled);
    }
}
