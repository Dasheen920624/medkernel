package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.task.RuntimeTaskBatchItem;
import com.medkernel.shared.runtime.task.RuntimeTaskMode;
import com.medkernel.shared.runtime.task.RuntimeTaskService;
import com.medkernel.shared.runtime.task.RuntimeTaskSubmitRequest;

/**
 * AIK-STD-14 公域资料自动获取 worker。调度只负责把到期来源提交到 SYS-05，失败补偿和死信复用运行任务框架。
 */
@Component
public class AcquisitionScheduleWorker {

    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final int MAX_RETRIES = 2;
    private static final String ACTOR = "knowledge-acquisition-scheduler";
    private static final DateTimeFormatter VERSION_SUFFIX =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final KnowledgeAcquisitionSourceRepository repository;
    private final RuntimeTaskService tasks;
    private final ObjectMapper objectMapper;

    public AcquisitionScheduleWorker(KnowledgeAcquisitionSourceRepository repository,
                                     RuntimeTaskService tasks,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
    }

    public AcquisitionScheduleSummary pollOnce() {
        Instant now = Instant.now();
        List<KnowledgeAcquisitionSource> dueSources = repository.findDueForSchedule(now, DEFAULT_SCAN_LIMIT);
        Map<String, List<RuntimeTaskBatchItem>> itemsByTenant = new LinkedHashMap<>();
        int claimed = 0;
        int skipped = 0;

        for (KnowledgeAcquisitionSource source : dueSources) {
            if (!source.isScheduleReady()) {
                skipped++;
                continue;
            }
            Instant nextCheckAt = now.plusSeconds(source.scheduleIntervalMinutes().longValue() * 60L);
            int updated = repository.markScheduleSubmitted(
                source.tenantId(), source.id(), source.version(), now, nextCheckAt, ACTOR);
            if (updated == 0) {
                skipped++;
                continue;
            }
            itemsByTenant.computeIfAbsent(source.tenantId(), ignored -> new ArrayList<>())
                .add(new RuntimeTaskBatchItem(source.sourceCode(), requestJson(source, now)));
            claimed++;
        }

        int submitted = 0;
        for (Map.Entry<String, List<RuntimeTaskBatchItem>> entry : itemsByTenant.entrySet()) {
            List<RuntimeTaskBatchItem> items = List.copyOf(entry.getValue());
            RequestContext.runWith(systemTenantContext(entry.getKey()), () -> tasks.submit(new RuntimeTaskSubmitRequest(
                RuntimeTaskMode.BATCH,
                AcquisitionRuntimeTaskHandler.TASK_TYPE,
                "{}",
                items,
                MAX_RETRIES)));
            submitted += items.size();
        }
        return new AcquisitionScheduleSummary(dueSources.size(), claimed, submitted, skipped);
    }

    private String requestJson(KnowledgeAcquisitionSource source, Instant now) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceCode", source.sourceCode());
        request.put("url", source.baseUrl());
        request.put("versionNo", "schedule-" + VERSION_SUFFIX.format(now));
        request.put("format", source.defaultFormat().name());
        attachGenerationPlan(request, source.generationPlanJson());
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "公域资料调度请求无法序列化", exception);
        }
    }

    private void attachGenerationPlan(ObjectNode request, String generationPlanJson) {
        if (generationPlanJson == null || generationPlanJson.isBlank()) {
            request.putNull("generation");
            return;
        }
        try {
            JsonNode generation = objectMapper.readTree(generationPlanJson);
            request.set("generation", generation);
        } catch (JsonProcessingException exception) {
            request.put("generation", generationPlanJson);
        }
    }

    private RequestContext.Snapshot systemTenantContext(String tenantId) {
        return new RequestContext.Snapshot(
            "knowledge-acquisition-schedule-" + UUID.randomUUID(),
            OrgScope.tenant(tenantId),
            ACTOR);
    }
}
