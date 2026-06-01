package com.medkernel.shared.observability;

import java.time.Instant;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * MedKernel v1.0 GA · GA-ENG-OBS-01 统一状态机跳转记录器。
 *
 * <p>所有引擎实体（context_snapshot / clinical_event / rule / pathway / ...）
 * 状态机跳转一律由本组件写历史。traceId / tenantId / actor 从 {@link RequestContext}
 * 自动注入，调用方无需关心。
 *
 * <p>同事务写历史：保证业务事务回滚则历史一并回滚，不会出现"业务无效但历史保留"
 * 导致追溯失真。RuntimeException 兜底为 WARN 日志（避免可观测组件自身 bug 反噬业务），
 * DataAccessException 仍向上抛由业务事务回滚处理。
 */
@Component
public class StateTransitionRecorder {

    private static final Logger log = LoggerFactory.getLogger(StateTransitionRecorder.class);

    private final StateTransitionHistoryRepository repository;

    public StateTransitionRecorder(StateTransitionHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一次实体状态机跳转。
     *
     * @param entityType  实体类型常量（如 "context_snapshot"、"clinical_event"）
     * @param entityId    业务 ID
     * @param fromStatus  起始状态；首次入库时填 null
     * @param toStatus    目标状态
     * @param reason      跳转原因（业务语义）
     * @param error       失败时的结构化错误；成功时填 null
     */
    public void record(String entityType, String entityId,
                       String fromStatus, String toStatus,
                       String reason, TransitionError error) {
        try {
            OrgScope scope = RequestContext.currentOrgScope();
            String tenantId = scope != null ? scope.tenantId() : null;
            String actor = RequestContext.currentUserId().orElse(null);
            String traceId = RequestContext.currentTraceId();
            Instant now = Instant.now();

            StateTransitionHistory entry = new StateTransitionHistory(
                null, entityType, entityId, tenantId, orgPath(scope),
                fromStatus, toStatus, reason, actor, traceId,
                error == null ? null : error.errorCode(),
                error == null ? null : error.errorClass(),
                error == null ? null : error.message(),
                error == null ? null : error.retryCount(),
                error == null ? null : error.nextRetryAt(),
                now,
                now,
                actor
            );
            repository.save(entry);
        } catch (DataAccessException e) {
            // 数据访问异常向上抛，业务事务回滚
            throw e;
        } catch (RuntimeException e) {
            // 非预期 RuntimeException 仅 warn，不阻塞业务
            log.warn("STATE_TRANSITION_RECORDER_FAILED entityType={} entityId={} reason={} cause={}",
                entityType, entityId, reason, e.toString());
        }
    }

    private static String orgPath(OrgScope scope) {
        if (scope == null) {
            return null;
        }
        String path = Stream.of(
                scope.tenantId(), scope.groupId(), scope.hospitalId(), scope.campusId(),
                scope.siteId(), scope.departmentId(), scope.specialtyId())
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + "/" + right)
            .orElse(null);
        return path == null || path.isBlank() ? null : path;
    }
}
