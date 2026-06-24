package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * LLM-04 prompt/tool/model 版本治理服务测试。
 */
class ModelVersionGovernanceServiceTest {

    private ModelVersionBundleRepository repository;
    private AuditRecorder auditRecorder;
    private ModelVersionGovernanceService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(ModelVersionBundleRepository.class);
        auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
        service = new ModelVersionGovernanceService(repository, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-version", OrgScope.tenant("tenant-1"), "ops"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void publishBundleHashesContentRetiresPreviousAndAudits() {
        when(repository.save(any(ModelVersionBundle.class))).thenAnswer(invocation -> {
            ModelVersionBundle bundle = invocation.getArgument(0);
            return new ModelVersionBundle(
                7L, bundle.tenantId(), bundle.capabilityCode(), bundle.promptVersion(), bundle.promptHash(),
                bundle.toolVersion(), bundle.toolHash(), bundle.modelVersion(), bundle.modelHash(),
                bundle.status(), bundle.activeScopeKey(), bundle.effectiveAt(), bundle.retiredAt(),
                bundle.createdAt(), bundle.createdBy(),
                bundle.updatedAt(), bundle.updatedBy());
        });

        ModelVersionBundleResponse response = service.publish(new ModelVersionBundleRequest(
            "rule.draft",
            "prompt:aikstd13-v1",
            "生成规则候选的受控提示词",
            "tool:submit-candidate-v1",
            "{\"name\":\"submitProductionCandidate\"}",
            "model:claude-opus-4",
            "claude-opus-4"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.promptHash()).hasSize(64);
        assertThat(response.toolHash()).hasSize(64);
        assertThat(response.modelHash()).hasSize(64);
        assertThat(response.promptHash()).doesNotContain("生成规则");
        ArgumentCaptor<ModelVersionBundle> savedBundle = ArgumentCaptor.forClass(ModelVersionBundle.class);
        verify(repository).save(savedBundle.capture());
        assertThat(savedBundle.getValue().activeScopeKey()).isEqualTo("tenant-1|rule.draft");
        verify(repository).retireActive("tenant-1", "rule.draft", "ops", response.effectiveAt());
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_model_version_bundle", "7",
            "发布提示词、工具和模型版本组合 rule.draft");
    }

    @Test
    void publishBundleRejectsBlankVersionPayloadBeforeRetiringActiveBundle() {
        assertThatThrownBy(() -> service.publish(new ModelVersionBundleRequest(
            "rule.draft",
            " ",
            "生成规则候选的受控提示词",
            "tool:submit-candidate-v1",
            "{\"name\":\"submitProductionCandidate\"}",
            "model:claude-opus-4",
            "claude-opus-4")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("prompt_version");

        verify(repository, never()).retireActive(any(), any(), any(), any());
        verify(repository, never()).save(any(ModelVersionBundle.class));
    }

    @Test
    void rollbackActivatesHistoricalBundleAndRetiresCurrentOne() {
        ModelVersionBundle retired = bundle(3L, "RETIRED", "prompt:v1", "tool:v1", "model:v1");
        when(repository.findById(3L)).thenReturn(Optional.of(retired));
        when(repository.activateBundle(any(), any(), any(), any(), any(), any())).thenReturn(1);

        ModelVersionBundleResponse response = service.rollback("rule.draft", 3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(repository).retireActive(org.mockito.Mockito.eq("tenant-1"), org.mockito.Mockito.eq("rule.draft"),
            org.mockito.Mockito.eq("ops"), any(Instant.class));
        verify(repository).activateBundle(org.mockito.Mockito.eq(3L), org.mockito.Mockito.eq("tenant-1"),
            org.mockito.Mockito.eq("rule.draft"), org.mockito.Mockito.eq("tenant-1|rule.draft"),
            org.mockito.Mockito.eq("ops"), any(Instant.class));
    }

    @Test
    void rollbackActivationConflictDoesNotAuditOrReturnSuccess() {
        ModelVersionBundle retired = bundle(3L, "RETIRED", "prompt:v1", "tool:v1", "model:v1");
        when(repository.findById(3L)).thenReturn(Optional.of(retired));
        when(repository.activateBundle(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rollback("rule.draft", 3L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.CONFLICT);

        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void exportReturnsVersionMetadataAndHashesOnly() {
        when(repository.findByTenantIdAndCapabilityCodeOrderByIdDesc("tenant-1", "rule.draft"))
            .thenReturn(List.of(bundle(2L, "ACTIVE", "prompt:v2", "tool:v2", "model:v2")));

        ModelVersionExportResponse response = service.export("rule.draft");

        assertThat(response.bundles()).hasSize(1);
        assertThat(response.bundles().getFirst().promptVersion()).isEqualTo("prompt:v2");
        assertThat(response.bundles().getFirst().promptHash()).isEqualTo("p-hash");
        assertThat(response.toString()).doesNotContain("受控提示词正文");
    }

    private ModelVersionBundle bundle(Long id, String status, String promptVersion,
                                      String toolVersion, String modelVersion) {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        return new ModelVersionBundle(
            id, "tenant-1", "rule.draft", promptVersion, "p-hash", toolVersion, "t-hash",
            modelVersion, "m-hash", status, "ACTIVE".equals(status) ? "tenant-1|rule.draft" : null,
            now, "ACTIVE".equals(status) ? null : now,
            now, "ops", now, "ops");
    }
}
