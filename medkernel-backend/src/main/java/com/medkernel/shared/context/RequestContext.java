package com.medkernel.shared.context;

import java.util.Optional;
import java.util.UUID;

/**
 * MedKernel v1.0 GA · GA-ENG-BASE-03/01/02 请求级上下文。
 *
 * <p>承载贯穿整个请求生命周期的三个维度：
 * <ul>
 *   <li><b>追踪</b>：traceId — 由 {@code TraceIdFilter} 从 {@code X-Trace-Id} 头解析或生成，回写到响应头和日志 MDC</li>
 *   <li><b>组织</b>：{@link OrgScope} — 由身份解析阶段从 JWT claim 填充，所有数据查询必须按此过滤</li>
 *   <li><b>身份</b>：userId / roles — 由 Spring Security 解析 JWT 后填充</li>
 * </ul>
 *
 * <p>线程模型：基于 {@link ThreadLocal}，配合 Virtual Threads 一处请求一片上下文。
 * 异步任务请使用 {@link #snapshot()} 显式传递，并在子线程通过 {@link #restore(Snapshot)} 恢复。
 *
 * <p>TraceId 由入口过滤器写入；OrgScope 与 userId 由认证和数据范围切面写入。
 * 缺少组织上下文时，租户级数据访问会被统一拦截。
 */
public final class RequestContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    /**
     * 请求上下文快照（不可变）。
     *
     * @param traceId  请求级追踪 ID
     * @param orgScope 组织上下文，缺失时为 {@link OrgScope#empty()}
     * @param userId   认证用户 ID，未登录时为 null
     */
    public record Snapshot(String traceId, OrgScope orgScope, String userId) {

        public Snapshot {
            if (traceId == null || traceId.isBlank()) {
                traceId = generateTraceId();
            }
            if (orgScope == null) {
                orgScope = OrgScope.empty();
            }
        }

        public Snapshot withTrace(String newTraceId) {
            return new Snapshot(newTraceId, orgScope, userId);
        }

        public Snapshot withOrg(OrgScope newOrg) {
            return new Snapshot(traceId, newOrg, userId);
        }

        public Snapshot withUser(String newUserId) {
            return new Snapshot(traceId, orgScope, newUserId);
        }
    }

    public static Snapshot snapshot() {
        Snapshot s = CURRENT.get();
        return s != null ? s : new Snapshot(generateTraceId(), OrgScope.empty(), null);
    }

    /**
     * 返回当前线程的真实 ThreadLocal 快照；ThreadLocal 未设置时返回 null（区别于
     * {@link #snapshot()} 在缺失时会生成新 traceId 的 fallback 行为）。
     *
     * <p>跨线程传播工具（如 {@code TraceIdPropagator.wrap}）需要在 finally 阶段
     * 区分"原本无 context"与"原本有 context"，前者需要 {@link #clear()}，后者需要
     * {@link #restore(Snapshot)}；用 {@link #snapshot()} 会让前者退化为后者并污染
     * 执行线程下一次任务。
     */
    public static Snapshot peekSnapshot() {
        return CURRENT.get();
    }

    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            clear();
        } else {
            CURRENT.set(snapshot);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String currentTraceId() {
        Snapshot s = CURRENT.get();
        return s != null ? s.traceId() : null;
    }

    public static OrgScope currentOrgScope() {
        Snapshot s = CURRENT.get();
        return s != null ? s.orgScope() : OrgScope.empty();
    }

    public static Optional<String> currentUserId() {
        Snapshot s = CURRENT.get();
        return s == null ? Optional.empty() : Optional.ofNullable(s.userId());
    }

    /**
     * 闭包风格：在指定快照下执行任务，结束后自动回滚到原快照。
     * 适用于异步线程恢复上下文、单元测试构造特定上下文。
     */
    public static <T> T callWith(Snapshot snapshot, java.util.concurrent.Callable<T> task) throws Exception {
        Snapshot prev = CURRENT.get();
        try {
            restore(snapshot);
            return task.call();
        } finally {
            restore(prev);
        }
    }

    public static void runWith(Snapshot snapshot, Runnable task) {
        Snapshot prev = CURRENT.get();
        try {
            restore(snapshot);
            task.run();
        } finally {
            restore(prev);
        }
    }

    static String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}
