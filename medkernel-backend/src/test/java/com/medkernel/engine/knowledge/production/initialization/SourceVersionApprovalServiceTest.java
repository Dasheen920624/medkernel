package com.medkernel.engine.knowledge.production.initialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 初始化来源版本独立审批测试。 */
class SourceVersionApprovalServiceTest {

    private final SourceVersionRepository versions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final SourceVersionApprovalRepository approvals = mock(SourceVersionApprovalRepository.class);
    private final HighRiskChangeGuard highRiskGuard = mock(HighRiskChangeGuard.class);
    private final SourceVersionApprovalService service =
        new SourceVersionApprovalService(versions, documents, approvals, highRiskGuard);

    @BeforeEach
    void bindContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "reviewer"));
        when(approvals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void rejectsSourceCreatorSelfApproval() {
        when(versions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(version("reviewer")));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(document()));

        assertThatThrownBy(() -> service.approve(9L, new SourceVersionApprovalRequest("确认官方版本")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源登记人与审批人必须分离");
    }

    @Test
    void approvesCompleteSourceAndBindsExactHash() {
        when(versions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(version("steward")));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(document()));
        when(approvals.findByTenantIdAndSourceVersionId("t-1", 9L)).thenReturn(Optional.empty());

        SourceVersionApproval approved =
            service.approve(9L, new SourceVersionApprovalRequest("已核对许可、发布机构和完整文件"));

        assertThat(approved.status()).isEqualTo(SourceVersionApprovalStatus.APPROVED);
        assertThat(approved.sourceHash()).isEqualTo("a".repeat(64));
        assertThat(approved.approvedBy()).isEqualTo("reviewer");
        assertThat(approved.reason()).contains("许可");
        verify(highRiskGuard).assertHighRiskAllowed("knowledge_source_version", "9");
    }

    private SourceVersion version(String creator) {
        return new SourceVersion(
            9L, "t-1", 7L, "2026.1", Instant.EPOCH, "a".repeat(64),
            "file:///managed/source.pdf", "zh-CN", Instant.EPOCH, creator);
    }

    private SourceDocument document() {
        return new SourceDocument(
            7L, "t-1", "OFFICIAL-1", SourceType.STANDARD, SourceAuthorityLevel.A_REGULATION,
            "国家正式发布", "官方标准", "发布机构", "正式许可", "zh-CN",
            Instant.EPOCH, "steward", Instant.EPOCH, "steward");
    }
}
