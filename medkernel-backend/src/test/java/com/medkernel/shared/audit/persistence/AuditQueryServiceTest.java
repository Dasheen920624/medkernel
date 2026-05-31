package com.medkernel.shared.audit.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.CursorRequest;
import com.medkernel.shared.api.CursorResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询服务的纯单元测试，重点验证：
 * <ul>
 *   <li>从 {@link RequestContext} 读取的 tenantId 被强制传入仓库</li>
 *   <li>无租户上下文时抛 {@link ApiException}（{@code TENANT_CONTEXT_MISSING}）</li>
 *   <li>游标分页：当仓库返回 size+1 行时截断并产出下一游标，否则 nextCursor 为 null</li>
 *   <li>非法 cursor 被拒绝</li>
 *   <li>过滤参数透传到仓库</li>
 * </ul>
 */
class AuditQueryServiceTest {

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void listRequiresTenantContext() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        AuditQueryService service = new AuditQueryService(repo);

        // no RequestContext set
        assertThatThrownBy(() -> service.list(CursorRequest.first(), null, null, null, null, null))
            .isInstanceOf(ApiException.class)
            .satisfies(t -> assertThat(((ApiException) t).errorCode())
                .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING));
    }

    @Test
    void listPassesTenantAndFiltersToRepository() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        when(repo.findPage(eq("t-1"), any(AuditEventQuery.class))).thenReturn(List.of());
        RequestContext.restore(snapshot("t-1"));

        AuditQueryService service = new AuditQueryService(repo);

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        service.list(new CursorRequest(null, 10), "PUBLISH", "rule", "u-1", from, to);

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(repo).findPage(eq("t-1"), captor.capture());
        AuditEventQuery q = captor.getValue();
        assertThat(q.action()).isEqualTo("PUBLISH");
        assertThat(q.resourceType()).isEqualTo("rule");
        assertThat(q.actorUserId()).isEqualTo("u-1");
        assertThat(q.from()).isEqualTo(from);
        assertThat(q.to()).isEqualTo(to);
        assertThat(q.cursor()).isNull();
        assertThat(q.size()).isEqualTo(10);
    }

    @Test
    void listPassesOrgEnvironmentOutcomeAndSuperAdminFiltersToRepository() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        when(repo.findPage(eq("t-1"), any(AuditEventQuery.class))).thenReturn(List.of());
        RequestContext.restore(snapshot("t-1"));

        AuditQueryService service = new AuditQueryService(repo);

        service.list(new CursorRequest(null, 20),
            "UPDATE",
            "audit_config",
            "system-super-admin",
            " tenant:t-1/hospital:h-1/ ",
            "prod",
            "FAILED",
            true,
            null,
            null);

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(repo).findPage(eq("t-1"), captor.capture());
        AuditEventQuery q = captor.getValue();
        assertThat(q.orgPathPrefix()).isEqualTo("tenant:t-1/hospital:h-1");
        assertThat(q.environmentKey()).isEqualTo("prod");
        assertThat(q.outcome()).isEqualTo("FAILED");
        assertThat(q.superAdminOnly()).isTrue();
    }

    @Test
    void truncatesAndEmitsNextCursorWhenRepositoryReturnsExtraRow() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        RequestContext.restore(snapshot("t-1"));

        List<AuditEventRecord> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(record((long) (10 - i), "evt-" + i));
        }
        when(repo.findPage(eq("t-1"), any(AuditEventQuery.class))).thenReturn(rows);

        AuditQueryService service = new AuditQueryService(repo);
        CursorResponse<AuditEventRecord> response = service.list(
            new CursorRequest(null, 3), null, null, null, null, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(String.valueOf(rows.get(2).id()));
    }

    @Test
    void noNextCursorWhenRepositoryReturnsLessThanSizePlusOne() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        RequestContext.restore(snapshot("t-1"));

        List<AuditEventRecord> rows = List.of(record(2L, "evt-2"), record(1L, "evt-1"));
        when(repo.findPage(eq("t-1"), any(AuditEventQuery.class))).thenReturn(rows);

        AuditQueryService service = new AuditQueryService(repo);
        CursorResponse<AuditEventRecord> response = service.list(
            new CursorRequest(null, 5), null, null, null, null, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void invalidCursorIsRejected() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        RequestContext.restore(snapshot("t-1"));

        AuditQueryService service = new AuditQueryService(repo);
        assertThatThrownBy(() -> service.list(
                new CursorRequest("not-a-number", 10), null, null, null, null, null))
            .isInstanceOf(ApiException.class)
            .satisfies(t -> assertThat(((ApiException) t).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void findByEventIdLooksUpUnderCurrentTenant() {
        AuditEventRepository repo = mock(AuditEventRepository.class);
        RequestContext.restore(snapshot("t-9"));

        when(repo.findByEventId(eq("t-9"), eq("evt-1")))
            .thenReturn(java.util.Optional.of(record(7L, "evt-1")));

        AuditQueryService service = new AuditQueryService(repo);
        java.util.Optional<AuditEventRecord> found = service.findByEventId("evt-1");
        assertThat(found).isPresent();
        verify(repo).findByEventId("t-9", "evt-1");
    }

    private static RequestContext.Snapshot snapshot(String tenantId) {
        return new RequestContext.Snapshot("trace-" + tenantId,
            OrgScope.tenant(tenantId), "user-1");
    }

    private static AuditEventRecord record(Long id, String eventId) {
        Instant now = Instant.now();
        return new AuditEventRecord(
            id, eventId, "trace", now, "user-1", "CREATE", "rule", "r-" + id,
            "summary", "digest", "t-1", null, null, null, "GENESIS",
            "sig-" + id, "SIGNED", "SUCCESS", null, now);
    }
}
