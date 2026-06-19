package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 来源版本独立批准服务。 */
@Service
public class SourceVersionApprovalService {

    private final SourceVersionRepository versions;
    private final SourceDocumentRepository documents;
    private final SourceVersionApprovalRepository approvals;
    private final HighRiskChangeGuard highRiskGuard;

    public SourceVersionApprovalService(
            SourceVersionRepository versions,
            SourceDocumentRepository documents,
            SourceVersionApprovalRepository approvals,
            HighRiskChangeGuard highRiskGuard) {
        this.versions = versions;
        this.documents = documents;
        this.approvals = approvals;
        this.highRiskGuard = highRiskGuard;
    }

    @Transactional
    public SourceVersionApproval approve(Long sourceVersionId, SourceVersionApprovalRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        if (request == null || blank(request.reason())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源版本批准必须填写核验理由");
        }
        highRiskGuard.assertHighRiskAllowed("knowledge_source_version", sourceVersionId.toString());
        SourceVersion version = versions.findByTenantIdAndId(tenantId, sourceVersionId)
            .orElseThrow(() -> ApiException.notFound("来源版本 id=" + sourceVersionId));
        SourceDocument document = documents.findByTenantIdAndId(tenantId, version.sourceDocumentId())
            .orElseThrow(() -> ApiException.notFound("来源文档 id=" + version.sourceDocumentId()));
        validateComplete(version, document);
        if (actor.equals(version.createdBy()) || actor.equals(document.createdBy())) {
            throw new ApiException(ErrorCode.CONFLICT, "来源登记人与审批人必须分离");
        }
        return approvals.findByTenantIdAndSourceVersionId(tenantId, sourceVersionId)
            .map(existing -> {
                if (existing.status() == SourceVersionApprovalStatus.APPROVED
                        && version.contentHash().equals(existing.sourceHash())) {
                    return existing;
                }
                throw new ApiException(ErrorCode.CONFLICT, "来源版本已有不同摘要或已撤销的批准记录");
            })
            .orElseGet(() -> {
                Instant now = Instant.now();
                return approvals.save(new SourceVersionApproval(
                    null,
                    tenantId,
                    version.id(),
                    version.contentHash(),
                    SourceVersionApprovalStatus.APPROVED,
                    actor,
                    now,
                    request.reason().trim(),
                    now,
                    actor));
            });
    }

    private void validateComplete(SourceVersion version, SourceDocument document) {
        if (blank(version.versionNo()) || !hash(version.contentHash()) || blank(version.fileUri())
                || document.authorityLevel() == null || blank(document.authorityBasis())
                || blank(document.title()) || blank(document.publisher()) || blank(document.license())) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "来源版本缺少官方版本、文件摘要、受管原件、权威依据、发布机构或许可");
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(actor -> !actor.isBlank())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "缺少来源版本审批人"));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
