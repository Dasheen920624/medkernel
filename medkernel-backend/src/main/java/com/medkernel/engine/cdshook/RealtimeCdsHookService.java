package com.medkernel.engine.cdshook;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.observability.TraceIdPropagator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * P13-5 实时 CDS Hook 门面：医生动作点同步取卡，超时与上下文不可用时诚实降级。
 */
@Service
public class RealtimeCdsHookService {

    private final RecommendationEngineService recommendations;
    private final AsyncTaskExecutor taskExecutor;
    private final RealtimeCdsProperties properties;
    private final SystemConfigService systemConfigService;

    @Autowired
    public RealtimeCdsHookService(RecommendationEngineService recommendations,
                                  @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor,
                                  RealtimeCdsProperties properties,
                                  SystemConfigService systemConfigService) {
        this.recommendations = recommendations;
        this.taskExecutor = taskExecutor == null
            ? new SimpleAsyncTaskExecutor("realtime-cds-")
            : taskExecutor;
        this.properties = properties == null ? new RealtimeCdsProperties() : properties;
        this.systemConfigService = systemConfigService;
    }

    RealtimeCdsHookService(RecommendationEngineService recommendations,
                           AsyncTaskExecutor taskExecutor,
                           RealtimeCdsProperties properties) {
        this(recommendations, taskExecutor, properties, null);
    }

    public CdsHookResponse evaluate(CdsHookRequest request) {
        CdsHookContract.requireCompleteContext(request);
        Duration budget = budgetFor(request.hook());
        RecommendationTriggerRequest triggerRequest = toRecommendationTrigger(request);
        if (triggerRequest.contextSnapshotId() == null) {
            return unavailable(request, budget, "上下文缺少标准上下文标识，无法读取已生效标准上下文");
        }
        Future<CdsHookResponse> future = taskExecutor.submit(TraceIdPropagator.wrap(() ->
            CdsHookResponse.fromRecommendationEvaluation(recommendations.evaluate(triggerRequest))));
        try {
            return future.get(budget.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return unavailable(request, budget, "超过 " + budget.toMillis() + "ms 硬超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable(request, budget, "求值线程被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return unavailable(request, budget, "求值不可用: " + cause.getMessage());
        }
    }

    private RecommendationTriggerRequest toRecommendationTrigger(CdsHookRequest request) {
        JsonNode context = request.context();
        return new RecommendationTriggerRequest(
            request.hookInstance(),
            request.hook().wireValue(),
            firstText(context, "sourceEventId", "eventId"),
            text(context, "contextSnapshotId"),
            request.patientId(),
            request.encounterId(),
            text(context, "patientPathwayId"),
            textOrDefault(context, "scenarioCode", request.hook().wireValue()),
            text(context, "inputDigest"),
            instant(context, "occurredAt"),
            List.of(),
            Boolean.FALSE
        );
    }

    private Duration budgetFor(ClinicalEventTriggerPoint hook) {
        long millis;
        if (systemConfigService != null) {
            millis = hook == ClinicalEventTriggerPoint.ORDER_SIGN
                ? systemConfigService.runtimeRealtimeCdsOrderSignTimeoutMs(properties)
                : systemConfigService.runtimeRealtimeCdsDefaultTimeoutMs(properties);
        } else {
            Duration configured = hook == ClinicalEventTriggerPoint.ORDER_SIGN
                ? properties.orderSignTimeout()
                : properties.defaultTimeout();
            millis = Math.max(1L, configured.toMillis());
        }
        return Duration.ofMillis(Math.max(1L, millis));
    }

    private CdsHookResponse unavailable(CdsHookRequest request, Duration budget, String reason) {
        String hook = request.hook() == null ? "unknown-hook" : request.hook().wireValue();
        String hookInstance = request.hookInstance() == null || request.hookInstance().isBlank()
            ? hook
            : request.hookInstance();
        CdsHookCard card = new CdsHookCard(
            hookInstance + "-cds-unavailable",
            "CDS 求值不可用，需人工核查",
            hook + " 实时 CDS " + reason + "，预算 " + budget.toMillis()
                + "ms；未静默放过，高危医嘱需医师人工核查并确认后继续。",
            "critical",
            new CdsHookSource("MedKernel 实时 CDS", null, "人工核查"),
            List.of(),
            List.of("已完成人工核查并记录原因"),
            true);
        return new CdsHookResponse(CdsHookContract.CURRENT_VERSION, List.of(card), List.of());
    }

    private static String firstText(JsonNode node, String firstField, String secondField) {
        String first = text(node, firstField);
        return first == null ? text(node, secondField) : first;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
