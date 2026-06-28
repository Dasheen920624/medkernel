package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.material.DocumentMaterialStoragePort;
import com.medkernel.engine.knowledge.material.DocumentMaterialStoreRequest;
import com.medkernel.engine.knowledge.material.StoredDocumentMaterial;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationOrchestrationService;
import com.medkernel.engine.knowledge.production.generation.GenerationItem;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
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
    private final SourceVersionRepository sourceVersionRepository;
    private final List<DocumentParser> parsers;
    private final ParsedDocumentMaterializer materializer;
    private final DocumentMaterialStoragePort materialStorage;
    private final CandidateGenerationOrchestrationService candidateGeneration;
    private final AuditRecorder auditRecorder;

    public DocumentParseOrchestrationService(DocParseJobRepository jobRepository,
                                             SourceDocumentRepository sourceDocumentRepository,
                                             SourceVersionRepository sourceVersionRepository,
                                             List<DocumentParser> parsers,
                                             ParsedDocumentMaterializer materializer,
                                             DocumentMaterialStoragePort materialStorage,
                                             CandidateGenerationOrchestrationService candidateGeneration,
                                             AuditRecorder auditRecorder) {
        this.jobRepository = jobRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceVersionRepository = sourceVersionRepository;
        this.parsers = parsers;
        this.materializer = materializer;
        this.materialStorage = materialStorage;
        this.candidateGeneration = candidateGeneration;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public DocParseJob submit(DocumentParseRequest request) {
        String tenantId = requireCurrentTenant();
        byte[] rawBytes = resolveRawBytes(request);
        SourceDocument sourceDoc = sourceDocumentRepository
            .findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> ApiException.notFound("受控来源"));

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
            return fail(pending, "解析格式 " + request.format() + " 的解析器待配置，未生成知识结构", actor);
        }

        ParsedDocument parsed;
        try {
            parsed = parser.parse(new ParseInput(sourceDoc.id(), request.versionNo(),
                request.fileName(), request.format(), rawBytes, actor));
        } catch (DocumentParseException e) {
            return fail(pending, e.getMessage(), actor);
        }

        StoredDocumentMaterial material = materialStorage.store(new DocumentMaterialStoreRequest(
            tenantId,
            tenantId,
            rawBytes,
            request.fileName(),
            contentType(request.format()),
            sourceHash,
            "DOC_PARSE",
            actor));

        MaterializationResult result = materializer.materialize(tenantId, sourceDoc.id(),
            request.versionNo(), material.fileUri(), sourceHash, parsed, actor);

        DocParseJob done = jobRepository.save(new DocParseJob(
            pending.id(), tenantId, jobCode, sourceDoc.id(), request.fileName(), request.format(),
            sourceHash, ParseJobStatus.SUCCEEDED, result.sourceVersionId(),
            result.sectionCount(), result.fragmentCount(), null, pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", jobCode,
            "文档解析成功：章节 " + result.sectionCount() + " 片段 " + result.fragmentCount());
        return done;
    }

    @Transactional
    public DocumentParseResponse submitTenantUpload(DocumentParseRequest request,
                                                    DocumentUploadGenerationRequest generation) {
        validateUploadGeneration(generation);
        DocParseJob parseJob = submit(request);
        if (generation == null
            || parseJob.status() != ParseJobStatus.SUCCEEDED
            || parseJob.resultSourceVersionId() == null) {
            return new DocumentParseResponse(parseJob, null);
        }
        GenerationSummary summary =
            candidateGeneration.generate(generation.toCandidateGenerationRequest(parseJob.resultSourceVersionId()));
        return new DocumentParseResponse(parseJob, summary);
    }

    @Transactional
    public DocParseJob reparse(String jobCode) {
        String tenantId = requireCurrentTenant();
        String actor = RequestContext.currentUserId().orElse(null);
        DocParseJob original = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("解析 job"));
        if (original.status() != ParseJobStatus.SUCCEEDED || original.resultSourceVersionId() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "只有已成功物化的解析 job 可以从资料库重解析");
        }
        SourceVersion version = sourceVersionRepository.findByTenantIdAndId(tenantId, original.resultSourceVersionId())
            .orElseThrow(() -> ApiException.notFound("来源版本"));
        byte[] rawBytes = materialStorage.fetch(tenantId, version.fileUri());
        String sourceHash = Sha256ContentHash.sha256Bytes(rawBytes, EMPTY_MSG);
        if (!sourceHash.equals(Sha256ContentHash.normalizeExternalSha256(original.sourceHash()))
            || !sourceHash.equals(Sha256ContentHash.normalizeExternalSha256(version.contentHash()))) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "重解析原件指纹与来源版本不一致，禁止继续");
        }

        String newJobCode = "dpj:" + UUID.randomUUID();
        Instant now = Instant.now();
        DocParseJob pending = jobRepository.save(new DocParseJob(
            null, tenantId, newJobCode, version.sourceDocumentId(), original.sourceFileName(),
            original.documentFormat(), sourceHash, ParseJobStatus.PENDING, null, null, null, null,
            now, actor, now, actor));

        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(original.documentFormat()))
            .findFirst()
            .orElse(null);
        if (parser == null) {
            return fail(pending, "解析格式 " + original.documentFormat() + " 的解析器待配置，未生成知识结构", actor);
        }

        ParsedDocument parsed;
        try {
            parsed = parser.parse(new ParseInput(version.sourceDocumentId(), version.versionNo(),
                original.sourceFileName(), original.documentFormat(), rawBytes, actor));
        } catch (DocumentParseException e) {
            return fail(pending, e.getMessage(), actor);
        }

        MaterializationResult result = materializer.materialize(tenantId, version.sourceDocumentId(),
            version.versionNo(), version.fileUri(), sourceHash, parsed, actor);

        DocParseJob done = jobRepository.save(new DocParseJob(
            pending.id(), tenantId, newJobCode, version.sourceDocumentId(), original.sourceFileName(),
            original.documentFormat(), sourceHash, ParseJobStatus.SUCCEEDED, result.sourceVersionId(),
            result.sectionCount(), result.fragmentCount(), null, pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", newJobCode,
            "文档从资料库重解析成功：章节 " + result.sectionCount() + " 片段 " + result.fragmentCount());
        return done;
    }

    public DocParseJob getJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("解析 job"));
    }

    public PageResponse<DocParseJob> listJobs(int page, int size) {
        String tenantId = requireCurrentTenant();
        PageRequest pageRequest = new PageRequest(page, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE), null);
        long total = jobRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        List<DocParseJob> items = jobRepository.pageByTenantId(
            tenantId, pageRequest.offset(), pageRequest.safeSize());
        return PageResponse.of(items, pageRequest, total);
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

    private static void validateUploadGeneration(DocumentUploadGenerationRequest generation) {
        if (generation == null) {
            return;
        }
        if (generation.domain() == null || generation.items() == null || generation.items().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内上传候选生成计划须声明领域和至少一个生成项");
        }
        for (GenerationItem item : generation.items()) {
            if (item == null || item.assetType() == null || item.target() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "院内上传候选生成项须声明资产类型和物化目标");
            }
            try {
                item.target().validate();
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ErrorCode.BAD_REQUEST, exception.getMessage());
            }
        }
    }

    /**
     * 按格式解析原文字节：结构化文本走 UTF-8；PDF/Word 二进制经 {@code content} 字段以 Base64 承载，
     * 非法 Base64 即结构化 400（请求体不合法），绝不静默吞错。
     */
    private byte[] resolveRawBytes(DocumentParseRequest request) {
        if (request.format() == DocumentFormat.STRUCTURED_TEXT) {
            return request.content().getBytes(UTF_8);
        }
        try {
            return Base64.getDecoder().decode(request.content());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "二进制格式 content 须为合法 Base64 编码");
        }
    }

    private static String contentType(DocumentFormat format) {
        return switch (format) {
            case STRUCTURED_TEXT -> "text/plain; charset=UTF-8";
            case PDF -> "application/pdf";
            case WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        };
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
