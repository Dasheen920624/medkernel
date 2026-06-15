package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 文档解析编排服务（AIK-STD-02）。job 生命周期：建 PENDING → 按格式分派解析器 →
 * 成功物化进 source_version/fragment 记 SUCCEEDED；解析失败/不支持格式诚实记 FAILED（FR-5，绝不产伪结构）。
 * 强租户隔离 + 审计。同步执行（管线可后续异步化，状态机骨架已具备）。
 */
@Service
public class DocumentParseOrchestrationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String EMPTY_MSG = "原文内容不能为空";

    private final DocParseJobRepository jobRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final List<DocumentParser> parsers;
    private final ParsedDocumentMaterializer materializer;
    private final AuditRecorder auditRecorder;

    public DocumentParseOrchestrationService(DocParseJobRepository jobRepository,
                                             SourceDocumentRepository sourceDocumentRepository,
                                             List<DocumentParser> parsers,
                                             ParsedDocumentMaterializer materializer,
                                             AuditRecorder auditRecorder) {
        this.jobRepository = jobRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.parsers = parsers;
        this.materializer = materializer;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public DocParseJob submit(DocumentParseRequest request) {
        String tenantId = requireCurrentTenant();
        SourceDocument sourceDoc = sourceDocumentRepository
            .findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> ApiException.notFound("受控来源"));

        byte[] rawBytes = request.content().getBytes(UTF_8);
        String sourceHash = Sha256ContentHash.sha256Bytes(rawBytes, EMPTY_MSG);
        String jobCode = "dpj:" + UUID.randomUUID();
        String actor = RequestContext.currentUserId().orElse(null);
        Instant now = Instant.now();

        DocParseJob pending = jobRepository.save(new DocParseJob(
            null, tenantId, jobCode, sourceDoc.id(), request.fileName(), request.format(),
            sourceHash, ParseJobStatus.PENDING, null, null, null, null, now, actor, now, actor));

        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(request.format()))
            .findFirst()
            .orElse(null);
        if (parser == null) {
            return fail(pending, "暂不支持解析格式 " + request.format() + "，待对应适配器接入", actor);
        }

        ParsedDocument parsed;
        try {
            parsed = parser.parse(new ParseInput(sourceDoc.id(), request.versionNo(),
                request.fileName(), request.format(), rawBytes, actor));
        } catch (DocumentParseException e) {
            return fail(pending, e.getMessage(), actor);
        }

        MaterializationResult result = materializer.materialize(tenantId, sourceDoc.id(),
            request.versionNo(), "doc-parse:" + jobCode, sourceHash, parsed, actor);

        DocParseJob done = jobRepository.save(new DocParseJob(
            pending.id(), tenantId, jobCode, sourceDoc.id(), request.fileName(), request.format(),
            sourceHash, ParseJobStatus.SUCCEEDED, result.sourceVersionId(),
            result.sectionCount(), result.fragmentCount(), null, pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", jobCode,
            "文档解析成功：章节 " + result.sectionCount() + " 片段 " + result.fragmentCount());
        return done;
    }

    public DocParseJob getJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("解析 job"));
    }

    public List<DocParseJob> listJobs(int page, int size) {
        String tenantId = requireCurrentTenant();
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return jobRepository.pageByTenantId(tenantId, safePage * safeSize, safeSize);
    }

    private DocParseJob fail(DocParseJob pending, String error, String actor) {
        DocParseJob failed = jobRepository.save(new DocParseJob(
            pending.id(), pending.tenantId(), pending.jobCode(), pending.sourceDocumentId(),
            pending.sourceFileName(), pending.documentFormat(), pending.sourceHash(),
            ParseJobStatus.FAILED, null, null, null, error,
            pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", pending.jobCode(),
            "文档解析失败：" + error);
        return failed;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
