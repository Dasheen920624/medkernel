package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.SimpleTriggerContext;

import com.medkernel.shared.config.SystemConfigService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetirementSchedulerTest {

    @Test
    void registersDynamicTaskAndReadsLatestIntervalForEveryExecution() {
        KnowledgeRetirementService retirementService = mock(KnowledgeRetirementService.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.runtimeKnowledgeRetirementIntervalMs()).thenReturn(45_000L);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new KnowledgeRetirementScheduler(retirementService, configService).configureTasks(registrar);

        List<TriggerTask> tasks = registrar.getTriggerTaskList();
        assertThat(tasks).hasSize(1);
        tasks.getFirst().getRunnable().run();
        verify(retirementService).finalizeDueRetirements();

        Instant completedAt = Instant.parse("2026-06-09T00:00:00Z");
        SimpleTriggerContext context = new SimpleTriggerContext(
            completedAt.minusSeconds(2),
            completedAt.minusSeconds(1),
            completedAt);
        assertThat(tasks.getFirst().getTrigger().nextExecution(context))
            .isEqualTo(completedAt.plusMillis(45_000L));
        verify(configService).runtimeKnowledgeRetirementIntervalMs();
    }
}
