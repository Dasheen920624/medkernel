package com.medkernel.engine.knowledge.acquisition;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.parsing.DocParseJob;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.engine.knowledge.parsing.DocumentParseOrchestrationService;
import com.medkernel.engine.knowledge.parsing.DocumentParseRequest;
import com.medkernel.engine.knowledge.parsing.ParseJobStatus;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
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
 * 公域知识资料获取编排（AIK-STD-14）。
 *
 * <p>仅生产中心可触发；URL 必须命中已审批白名单，许可和 robots 策略必须允许。抓取到的真实字节进入
 * AIK-STD-02 文档解析链路，由既有资料库存储端口决定落本地磁盘、对象存储或 HTTPS 网关。
 */
@Service
public class AcquisitionOrchestrationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter VERSION_SUFFIX =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final KnowledgeAcquisitionSourceRepository sourceRepository;
    private final KnowledgeAcquisitionRunRepository runRepository;
    private final SourceDocumentRepository sourceDocuments;
    private final SourceVersionRepository sourceVersions;
    private final DocumentParseOrchestrationService parseService;
    private final DeploymentFormService deploymentFormService;
    private final WebContentFetcher fetcher;
    private final AuditRecorder auditRecorder;

    public AcquisitionOrchestrationService(KnowledgeAcquisitionSourceRepository sourceRepository,
                                           KnowledgeAcquisitionRunRepository runRepository,
                                           SourceDocumentRepository sourceDocuments,
                                           SourceVersionRepository sourceVersions,
                                           DocumentParseOrchestrationService parseService,
                                           DeploymentFormService deploymentFormService,
                                           WebContentFetcher fetcher,
                                           AuditRecorder auditRecorder) {
        this.sourceRepository = sourceRepository;
        this.runRepository = runRepository;
        this.sourceDocuments = sourceDocuments;
        this.sourceVersions = sourceVersions;
        this.parseService = parseService;
        this.deploymentFormService = deploymentFormService;
        this.fetcher = fetcher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public KnowledgeAcquisitionRunResponse run(KnowledgeAcquisitionRunRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = RequestContext.currentUserId().orElse(null);
        Instant now = Instant.now();
        String runCode = "acq:" + UUID.randomUUID();
        URI uri = parseUri(request.url());
        String domain = normalizeHost(uri.getHost());

        if (deploymentFormService.currentForm() != DeploymentForm.PRODUCTION_CENTER) {
            return saveBlocked(tenantId, runCode, null, request, domain,
                "公域资料获取仅允许 PRODUCTION_CENTER 运行，本实例不是生产中心", actor, now);
        }

        KnowledgeAcquisitionSource source = sourceRepository
            .findByTenantIdAndSourceCode(tenantId, request.sourceCode())
            .orElse(null);
        if (source == null) {
            return saveBlocked(tenantId, runCode, null, request, domain,
                "来源未进入公域获取白名单：" + request.sourceCode(), actor, now);
        }
        String sourceDomain = normalizeDomain(source.domain());
        if (!source.isEffective()) {
            return saveBlocked(tenantId, runCode, source, request, domain,
                "来源未启用、未审批、许可不允许或 robots 策略不允许：" + request.sourceCode(), actor, now);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return saveBlocked(tenantId, runCode, source, request, domain,
                "公域资料获取仅允许 HTTPS URL", actor, now);
        }
        if (!matchesDomain(domain, sourceDomain)) {
            return saveBlocked(tenantId, runCode, source, request, domain,
                "URL 域名不在来源白名单：" + domain, actor, now);
        }

        FetchedWebContent fetched;
        try {
            fetched = fetcher.fetch(uri);
        } catch (RuntimeException exception) {
            return saveFailed(tenantId, runCode, source, request, domain, null, null,
                null, null, null, "公域资料抓取失败：" + exception.getMessage(), actor, now);
        }
        String effectiveDomain = normalizeHost(fetched.effectiveUri().getHost());
        if (!matchesDomain(effectiveDomain, sourceDomain)) {
            return saveBlocked(tenantId, runCode, source, request, domain,
                "重定向域名不在来源白名单：" + effectiveDomain, actor, now);
        }
        byte[] bytes = fetched.bytes();
        String sourceHash = Sha256ContentHash.sha256Bytes(bytes, "公域资料原文不能为空");
        SourceDocument sourceDocument = ensureSourceDocument(tenantId, source, actor, now);

        Optional<SourceVersion> duplicate = sourceVersions
            .findBySourceDocumentIdAndContentHash(sourceDocument.id(), sourceHash);
        if (duplicate.isPresent()) {
            SourceVersion version = duplicate.get();
            KnowledgeAcquisitionRun run = saveRun(tenantId, runCode, source, request, domain,
                KnowledgeAcquisitionRunStatus.DUPLICATE, fetched.fetchedAt(), sourceHash, (long) bytes.length,
                contentType(fetched, request.format()), version.fileUri(), sourceDocument.id(), version.id(),
                null, null, actor, now);
            auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_acquisition_run", runCode,
                "公域资料重复复用：" + source.sourceCode());
            return KnowledgeAcquisitionRunResponse.from(run);
        }

        DocParseJob parseJob = parseService.submit(new DocumentParseRequest(
            sourceDocument.id(),
            request.versionNo(),
            fileName(source.sourceCode(), request.versionNo(), request.format()),
            request.format(),
            requestContent(bytes, request.format())));
        if (parseJob.status() != ParseJobStatus.SUCCEEDED || parseJob.resultSourceVersionId() == null) {
            return saveFailed(tenantId, runCode, source, request, domain, fetched.fetchedAt(), sourceHash,
                (long) bytes.length, contentType(fetched, request.format()), parseJob.jobCode(),
                "文档解析失败：" + parseJob.errorMessage(), actor, now);
        }
        SourceVersion sourceVersion = sourceVersions
            .findByTenantIdAndId(tenantId, parseJob.resultSourceVersionId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVID_002, "解析成功但来源版本缺失，禁止伪造获取结果"));
        KnowledgeAcquisitionRun run = saveRun(tenantId, runCode, source, request, domain,
            KnowledgeAcquisitionRunStatus.SUCCEEDED, fetched.fetchedAt(), sourceHash, (long) bytes.length,
            contentType(fetched, request.format()), sourceVersion.fileUri(), sourceDocument.id(), sourceVersion.id(),
            parseJob.jobCode(), null, actor, now);
        auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_acquisition_run", runCode,
            "公域资料获取成功：" + source.sourceCode());
        return KnowledgeAcquisitionRunResponse.from(run);
    }

    public PageResponse<KnowledgeAcquisitionSource> listSources(int page, int size) {
        String tenantId = requireCurrentTenant();
        PageRequest pageRequest = new PageRequest(page, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE), null);
        long total = sourceRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(sourceRepository.pageByTenantId(tenantId, pageRequest.offset(), pageRequest.safeSize()),
            pageRequest, total);
    }

    public PageResponse<KnowledgeAcquisitionRun> listRuns(int page, int size) {
        String tenantId = requireCurrentTenant();
        PageRequest pageRequest = new PageRequest(page, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE), null);
        long total = runRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(runRepository.pageByTenantId(tenantId, pageRequest.offset(), pageRequest.safeSize()),
            pageRequest, total);
    }

    private KnowledgeAcquisitionRunResponse saveBlocked(String tenantId, String runCode,
                                                        KnowledgeAcquisitionSource source,
                                                        KnowledgeAcquisitionRunRequest request,
                                                        String domain, String reason,
                                                        String actor, Instant now) {
        KnowledgeAcquisitionRun run = saveRun(tenantId, runCode, source, request, domain,
            KnowledgeAcquisitionRunStatus.BLOCKED, null, null, null, null, null, null, null,
            null, reason, actor, now);
        auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_acquisition_run", runCode,
            "公域资料获取被阻断：" + reason);
        return KnowledgeAcquisitionRunResponse.from(run);
    }

    private KnowledgeAcquisitionRunResponse saveFailed(String tenantId, String runCode,
                                                       KnowledgeAcquisitionSource source,
                                                       KnowledgeAcquisitionRunRequest request,
                                                       String domain, Instant fetchedAt, String sourceHash,
                                                       Long byteSize, String contentType, String parseJobCode,
                                                       String reason, String actor, Instant now) {
        KnowledgeAcquisitionRun run = saveRun(tenantId, runCode, source, request, domain,
            KnowledgeAcquisitionRunStatus.FAILED, fetchedAt, sourceHash, byteSize, contentType, null,
            null, null, parseJobCode, reason, actor, now);
        auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_acquisition_run", runCode,
            "公域资料获取失败：" + reason);
        return KnowledgeAcquisitionRunResponse.from(run);
    }

    private KnowledgeAcquisitionRun saveRun(String tenantId, String runCode, KnowledgeAcquisitionSource source,
                                            KnowledgeAcquisitionRunRequest request, String domain,
                                            KnowledgeAcquisitionRunStatus status, Instant fetchedAt,
                                            String sourceHash, Long byteSize, String contentType,
                                            String materialFileUri, Long sourceDocumentId, Long sourceVersionId,
                                            String parseJobCode, String failureReason, String actor, Instant now) {
        return runRepository.save(new KnowledgeAcquisitionRun(
            null,
            tenantId,
            runCode,
            source == null ? null : source.id(),
            request.sourceCode(),
            request.url(),
            domain,
            AcquisitionTriggerType.MANUAL,
            status,
            fetchedAt,
            sourceHash,
            byteSize,
            contentType,
            source == null ? null : source.license(),
            source == null ? null : source.licensePolicy(),
            source == null ? null : source.robotsPolicy(),
            materialFileUri,
            sourceDocumentId,
            sourceVersionId,
            parseJobCode,
            failureReason,
            now,
            actor,
            Instant.now(),
            actor));
    }

    private SourceDocument ensureSourceDocument(String tenantId, KnowledgeAcquisitionSource source,
                                                String actor, Instant now) {
        return sourceDocuments.findByTenantIdAndSourceCode(tenantId, source.sourceCode())
            .orElseGet(() -> sourceDocuments.save(new SourceDocument(
                null,
                tenantId,
                source.sourceCode(),
                source.sourceType(),
                source.authorityLevel(),
                source.authorityBasis(),
                source.title(),
                source.publisher(),
                source.license(),
                "zh-CN",
                now,
                actor,
                now,
                actor)));
    }

    private URI parseUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "url 不能为空");
        }
        try {
            URI uri = new URI(raw);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "url 须为包含协议和域名的合法 URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "url 须为合法 URI");
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        return host.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "";
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                String host = URI.create(normalized).getHost();
                return host == null ? "" : host.toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException exception) {
                return "";
            }
        }
        return normalized;
    }

    private static boolean matchesDomain(String host, String domain) {
        return !host.isBlank() && !domain.isBlank()
            && (host.equals(domain) || host.endsWith("." + domain));
    }

    private static String fileName(String sourceCode, String versionNo, DocumentFormat format) {
        String cleanCode = sanitize(sourceCode);
        String cleanVersion = versionNo == null || versionNo.isBlank() ? VERSION_SUFFIX.format(Instant.now()) : sanitize(versionNo);
        return cleanCode + "-" + cleanVersion + extension(format);
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String extension(DocumentFormat format) {
        return switch (format) {
            case STRUCTURED_TEXT -> ".txt";
            case PDF -> ".pdf";
            case WORD -> ".docx";
        };
    }

    private static String requestContent(byte[] bytes, DocumentFormat format) {
        if (format == DocumentFormat.STRUCTURED_TEXT) {
            return new String(bytes, UTF_8);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String contentType(FetchedWebContent fetched, DocumentFormat format) {
        if (fetched.contentType() != null && !fetched.contentType().isBlank()) {
            return fetched.contentType();
        }
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
