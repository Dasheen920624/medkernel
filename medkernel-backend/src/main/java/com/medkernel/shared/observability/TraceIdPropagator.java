package com.medkernel.shared.observability;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.MDC;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 跨异步线程的请求与可观测性上下文传播工具类（Trace ID Propagator）。
 *
 * <p>在后台异步任务执行场景下，提供包括追踪号、请求组织上下文以及日志诊断映射上下文在内的多维属性跨线程传递包装。
 * 任务结束时能够精确恢复工作线程的原有上下文状态，防范线程池中的工作线程产生上下文数据污染。
 *
 * <p>支撑 GA-ENG-OBS-01 引擎可观测性骨干的多线程调用追踪号。
 */
public final class TraceIdPropagator {

    private TraceIdPropagator() {
    }

    /**
     * 包装 Runnable，自动复制当前线程的 RequestContext + MDC 到任务执行线程，
     * 任务结束精确恢复执行线程的原状态（含 ThreadLocal 未设置时彻底清理，
     * 避免线程池 worker 被随机 traceId 污染下一次任务）。
     */
    public static Runnable wrap(Runnable task) {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            RequestContext.Snapshot prev = RequestContext.peekSnapshot();
            Map<String, String> prevMdc = MDC.getCopyOfContextMap();
            try {
                RequestContext.restore(snapshot);
                setMdc(mdc);
                task.run();
            } finally {
                if (prev == null) {
                    RequestContext.clear();
                } else {
                    RequestContext.restore(prev);
                }
                setMdc(prevMdc);
            }
        };
    }

    /** 同上，泛型 Callable */
    public static <T> Callable<T> wrap(Callable<T> task) {
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            RequestContext.Snapshot prev = RequestContext.peekSnapshot();
            Map<String, String> prevMdc = MDC.getCopyOfContextMap();
            try {
                RequestContext.restore(snapshot);
                setMdc(mdc);
                return task.call();
            } finally {
                if (prev == null) {
                    RequestContext.clear();
                } else {
                    RequestContext.restore(prev);
                }
                setMdc(prevMdc);
            }
        };
    }

    /**
     * 后台 worker（OutboxWorker / @Scheduled）从 DB 恢复 traceId 时调用：
     * 在当前线程注入 RequestContext + MDC。完成后必须调 {@link #clear()}。
     */
    public static void restoreFromTrace(String traceId, String tenantId, String userId) {
        RequestContext.restore(new RequestContext.Snapshot(
            traceId, OrgScope.tenant(tenantId), userId));
        if (traceId != null) {
            MDC.put(MdcEnrichmentFilter.MDC_TRACE_ID, traceId);
        }
        if (tenantId != null) {
            MDC.put(MdcEnrichmentFilter.MDC_TENANT_ID, tenantId);
        }
        if (userId != null) {
            MDC.put(MdcEnrichmentFilter.MDC_USER_ID, userId);
        }
    }

    /** 清理当前线程 RequestContext + MDC */
    public static void clear() {
        RequestContext.clear();
        MDC.remove(MdcEnrichmentFilter.MDC_TRACE_ID);
        MDC.remove(MdcEnrichmentFilter.MDC_TENANT_ID);
        MDC.remove(MdcEnrichmentFilter.MDC_USER_ID);
        MDC.remove(MdcEnrichmentFilter.MDC_REQUEST_PATH);
    }

    private static void setMdc(Map<String, String> mdc) {
        MDC.clear();
        if (mdc != null) {
            mdc.forEach(MDC::put);
        }
    }
}
