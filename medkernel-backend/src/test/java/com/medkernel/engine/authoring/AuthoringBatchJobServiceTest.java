package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.pkg.PackageSyncResponse;
import com.medkernel.engine.pkg.ReleasePlanStatus;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleCreateResponse;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleGovernanceState;
import com.medkernel.engine.rule.RuleImpactResponse;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthoringBatchJobServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthoringBatchJobRepository jobs = mock(AuthoringBatchJobRepository.class);
    private final AuthoringBatchItemRepository items = mock(AuthoringBatchItemRepository.class);
    private final AuthoringBatchRulePort rules = mock(AuthoringBatchRulePort.class);
    private final AuthoringBatchPackagePort packages = mock(AuthoringBatchPackagePort.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final AuthoringBatchJobService service = new AuthoringBatchJobService(
        objectMapper,
        jobs,
        items,
        rules,
        packages,
        AuthoringFeatureGate.alwaysEnabled(),
        auditRecorder);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-batch", OrgScope.tenant("tenant-A"), "author-1"));
        AtomicLong ids = new AtomicLong();
        when(jobs.save(any())).thenAnswer(invocation -> {
            AuthoringBatchJob job = invocation.getArgument(0);
            return job.id() == null ? job.withId(ids.incrementAndGet()) : job;
        });
        when(items.save(any())).thenAnswer(invocation -> {
            AuthoringBatchItem item = invocation.getArgument(0);
            return item.id() == null ? item.withId(ids.incrementAndGet()) : item;
        });
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void generatesOneDraftPerRowAndKeepsSuccessfulRowsWhenAnotherRowFails() {
        ObjectNode templateDsl = objectMapper.createObjectNode()
            .set("meta", objectMapper.createObjectNode().putArray("parameters")
                .add(objectMapper.createObjectNode()
                    .put("key", "threshold")
                    .put("type", "DECIMAL")
                    .put("required", true)));
        when(rules.loadTemplate("rule-template"))
            .thenReturn(new AuthoringBatchRuleTemplate(
                "rule-template",
                RuleType.ORDER,
                RuleAuthoringMode.DSL,
                RuleRiskLevel.MEDIUM,
                100,
                null,
                0,
                "pkg-2026.06",
                "dept-1",
                "指南 2026",
                templateDsl,
                objectMapper.createObjectNode()));
        when(rules.createDraft(any()))
            .thenReturn(new RuleCreateResponse(
                "rule-generated-1", "version-1", RuleDefinitionStatus.DRAFT, "trace-batch"))
            .thenThrow(new ApiException(ErrorCode.BAD_REQUEST, "参数不完整"));

        AuthoringBatchJobResponse response = service.generateRules(
            new AuthoringBatchRuleGenerateRequest(
                "rule-template",
                List.of(
                    new AuthoringBatchRuleGenerateRow(
                        "row-1", "RULE.CKD.1", "CKD 阈值 1",
                        objectMapper.createObjectNode().put("threshold", 45),
                        null, null, "批量生成"),
                    new AuthoringBatchRuleGenerateRow(
                        "row-2", "RULE.CKD.2", "CKD 阈值 2",
                        objectMapper.createObjectNode(),
                        null, null, "批量生成"))));

        assertThat(response.status()).isEqualTo(AuthoringBatchJobStatus.PARTIAL_SUCCESS);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.items()).extracting(AuthoringBatchItemResponse::status)
            .containsExactly(AuthoringBatchItemStatus.SUCCEEDED, AuthoringBatchItemStatus.FAILED);
        assertThat(response.items().getFirst().targetId()).isEqualTo("rule-generated-1");
        verify(rules, org.mockito.Mockito.times(2)).createDraft(any());
    }

    @Test
    void rejectsHighRiskBatchPublishBeforeAnyTransition() {
        when(rules.impact("rule-high")).thenReturn(impact("rule-high", RuleRiskLevel.HIGH));

        assertThatThrownBy(() -> service.publishRules(
            new AuthoringBatchRulePublishRequest(
                RuleGovernanceState.FULL,
                "委员会批准",
                List.of(new AuthoringBatchRulePublishItem(
                    "row-high", "rule-high", "impact-rule-high", false)))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("高危");

        verify(rules, never()).transition(any(), any());
        verify(jobs, never()).save(any());
    }

    @Test
    void recordsUnreachableDistributionTargetAsNotConnected() {
        when(packages.distribute(any())).thenReturn(new PackageSyncResponse(
            "plan-1", "package-1", ReleasePlanStatus.NOT_SYNCED, List.of()));

        AuthoringBatchJobResponse response = service.distributePackages(
            new AuthoringBatchPackageDistributeRequest(List.of(
                new AuthoringBatchPackageDistributeItem(
                    "row-1",
                    "package-1",
                    "hospital-offline",
                    com.medkernel.engine.pkg.ReleaseStrategy.FULL,
                    com.medkernel.engine.pkg.ReleaseScopeType.FACILITY,
                    "hospital-offline",
                    List.of("fhir"),
                    "批量分发"))));

        assertThat(response.status()).isEqualTo(AuthoringBatchJobStatus.NOT_CONNECTED);
        assertThat(response.retryableCount()).isEqualTo(1);
        assertThat(response.items()).singleElement()
            .extracting(AuthoringBatchItemResponse::status)
            .isEqualTo(AuthoringBatchItemStatus.NOT_CONNECTED);
        ArgumentCaptor<AuthoringBatchItem> itemCaptor = ArgumentCaptor.forClass(AuthoringBatchItem.class);
        verify(items).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().targetId()).isEqualTo("hospital-offline");
        assertThat(itemCaptor.getValue().rollbackRef()).isEqualTo("plan-1");
    }

    @Test
    void listRecentReturnsTenantScopedPageInsteadOfTop50Snapshot() {
        AuthoringBatchJob row = job("abj-page-2", AuthoringBatchJobType.RULE_GENERATE);
        when(jobs.countByTenantId("tenant-A")).thenReturn(41L);
        when(jobs.pageByTenantId("tenant-A", 20, 20)).thenReturn(List.of(row));

        PageResponse<AuthoringBatchJobResponse> page = service.listRecent(new PageRequest(2, 20, null));

        assertThat(page.items()).extracting(AuthoringBatchJobResponse::jobId).containsExactly("abj-page-2");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(41L);
        assertThat(page.hasNext()).isTrue();
        verify(jobs).countByTenantId("tenant-A");
        verify(jobs).pageByTenantId("tenant-A", 20, 20);
    }

    private RuleImpactResponse impact(String ruleId, RuleRiskLevel riskLevel) {
        return new RuleImpactResponse(
            ruleId,
            "version-" + ruleId,
            riskLevel,
            "COMPLETE",
            "impact-" + ruleId,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "trace-batch");
    }

    private static AuthoringBatchJob job(String jobId, AuthoringBatchJobType type) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new AuthoringBatchJob(
            1L,
            jobId,
            "tenant-A",
            type,
            AuthoringBatchJobStatus.SUCCEEDED,
            1,
            1,
            0,
            0,
            "{}",
            null,
            now,
            "author-1",
            now,
            "author-1",
            "trace-batch");
    }
}
