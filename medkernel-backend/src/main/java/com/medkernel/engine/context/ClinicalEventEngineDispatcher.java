package com.medkernel.engine.context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.TraceIdPropagator;

/**
 * 将同一个 {@link ClinicalEventContext} 按固定顺序派发到规则、路径和 CDSS。
 */
@Component
public class ClinicalEventEngineDispatcher {

    private final Map<ClinicalEventEngine, ClinicalEventEngineAdapter> adapters;
    private final AsyncTaskExecutor taskExecutor;
    private final ClinicalEventProperties properties;
    private final SystemConfigService systemConfigService;

    public ClinicalEventEngineDispatcher(List<ClinicalEventEngineAdapter> adapters) {
        this(adapters, new SimpleAsyncTaskExecutor("clinical-event-dispatch-"), new ClinicalEventProperties());
    }

    @Autowired
    public ClinicalEventEngineDispatcher(List<ClinicalEventEngineAdapter> adapters,
                                         @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor,
                                         ClinicalEventProperties properties,
                                         SystemConfigService systemConfigService) {
        this.adapters = new EnumMap<>(ClinicalEventEngine.class);
        for (ClinicalEventEngineAdapter adapter : adapters == null ? List.<ClinicalEventEngineAdapter>of() : adapters) {
            this.adapters.put(adapter.engine(), adapter);
        }
        this.taskExecutor = taskExecutor == null
            ? new SimpleAsyncTaskExecutor("clinical-event-dispatch-")
            : taskExecutor;
        this.properties = properties == null ? new ClinicalEventProperties() : properties;
        this.systemConfigService = systemConfigService;
    }

    ClinicalEventEngineDispatcher(List<ClinicalEventEngineAdapter> adapters,
                                  AsyncTaskExecutor taskExecutor,
                                  ClinicalEventProperties properties) {
        this(adapters, taskExecutor, properties, null);
    }

    public List<ClinicalEventEngineDispatchResult> dispatch(ClinicalEventContext context) {
        long deadlineNanos = System.nanoTime() + syncBudget().toNanos();
        List<ClinicalEventEngineDispatchResult> results = new ArrayList<>();
        for (ClinicalEventEngine engine : ClinicalEventEngine.requiredEngines()) {
            results.add(dispatchWithinBudget(engine, context, deadlineNanos));
        }
        return List.copyOf(results);
    }

    private ClinicalEventEngineDispatchResult dispatchWithinBudget(
            ClinicalEventEngine engine,
            ClinicalEventContext context,
            long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return ClinicalEventEngineDispatchResult.unavailable(
                engine, null, "事件触发求值预算已耗尽，未继续派发 " + engine);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return dispatchInCurrentThread(engine, context);
        }
        Future<ClinicalEventEngineDispatchResult> future = taskExecutor.submit(dispatchTask(engine, context));
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return ClinicalEventEngineDispatchResult.unavailable(
                engine, null, "事件触发求值超时，预算 " + syncBudget().toMillis() + "ms，已进入人工核查");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ClinicalEventEngineDispatchResult.unavailable(
                engine, null, "事件触发求值线程被中断，已进入人工核查");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return ClinicalEventEngineDispatchResult.unavailable(
                engine, null, "事件触发求值不可用: " + cause.getMessage());
        }
    }

    private ClinicalEventEngineDispatchResult dispatchInCurrentThread(
            ClinicalEventEngine engine,
            ClinicalEventContext context) {
        try {
            return dispatchTask(engine, context).call();
        } catch (Exception exception) {
            return ClinicalEventEngineDispatchResult.unavailable(
                engine, null, "事件触发求值不可用: " + exception.getMessage());
        }
    }

    private Callable<ClinicalEventEngineDispatchResult> dispatchTask(
            ClinicalEventEngine engine,
            ClinicalEventContext context) {
        RequestContext.Snapshot eventSnapshot = new RequestContext.Snapshot(
            context.traceId(),
            context.orgScope(),
            RequestContext.currentUserId().orElse(null)
        );
        try {
            return RequestContext.callWith(
                eventSnapshot,
                () -> TraceIdPropagator.wrap(() -> adapter(engine).dispatch(context))
            );
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_005, "创建临床事件派发上下文失败");
        }
    }

    private Duration syncBudget() {
        if (systemConfigService != null) {
            return Duration.ofMillis(systemConfigService.runtimeClinicalEventSyncTimeoutMs(properties));
        }
        Duration configured = properties.syncTimeout();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return Duration.ofSeconds(3);
        }
        return configured;
    }

    private ClinicalEventEngineAdapter adapter(ClinicalEventEngine engine) {
        ClinicalEventEngineAdapter adapter = adapters.get(engine);
        if (adapter == null) {
            throw new ApiException(ErrorCode.ENG_EVENT_005, "缺少临床事件引擎适配器: " + engine);
        }
        return adapter;
    }
}
