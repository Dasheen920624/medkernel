package com.medkernel.engine.knowledge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识资产异步导出服务。
 *
 * <p>对外契约：
 * <ul>
 *   <li>{@code submit}：写入 PENDING 作业 + 事务提交后投递后台执行</li>
 *   <li>{@code get}：按 {@code jobCode} 查询当前状态</li>
 *   <li>{@code listRecent}：当前租户最近 100 个作业</li>
 *   <li>{@code cancel}：标记 PENDING/RUNNING 作业为 CANCELLED</li>
 * </ul>
 *
 * <p>实现策略：
 * <ul>
 *   <li>Job 持久化在 {@code knowledge_export_job}，对外可见 ID 是 {@code job_code}（UUID）</li>
 *   <li>{@code worker} 在线程池执行：PENDING → RUNNING → SUCCEEDED/FAILED</li>
 *   <li>导出内容先落本机 JSONL 文件，{@code result_uri} 返回当前 API 的下载端点</li>
 *   <li>结果 TTL 默认 7 天（{@code expires_at}），由 GA-ENG-PKG-01 清理任务 sweep</li>
 * </ul>
 */
@Service
public class KnowledgeExportService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExportService.class);
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final int EXPORT_BATCH_SIZE = 500;
    private static final String DOWNLOAD_PREFIX = "/api/v1/engine/knowledge/exports/";

    private final KnowledgeExportJobRepository jobRepository;
    private final KnowledgeIdentityRepository identityRepository;
    private final KnowledgeAssetVersionRepository versionRepository;
    private final KnowledgeSupersessionRepository supersessionRepository;
    private final CitationRepository citationRepository;
    private final KnowledgeInvalidationRepository invalidationRepository;
    private final AffectedCaseTaskRepository affectedCaseTaskRepository;
    private final ObjectMapper json;
    private final Executor knowledgeExportExecutor;
    private final Path exportDirectory;

    public KnowledgeExportService(KnowledgeExportJobRepository jobRepository,
                                  KnowledgeIdentityRepository identityRepository,
                                  KnowledgeAssetVersionRepository versionRepository,
                                  KnowledgeSupersessionRepository supersessionRepository,
                                  CitationRepository citationRepository,
                                  KnowledgeInvalidationRepository invalidationRepository,
                                  AffectedCaseTaskRepository affectedCaseTaskRepository,
                                  ObjectMapper json,
                                  @Qualifier("knowledgeExportExecutor") Executor knowledgeExportExecutor) {
        this.jobRepository = jobRepository;
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.citationRepository = citationRepository;
        this.invalidationRepository = invalidationRepository;
        this.affectedCaseTaskRepository = affectedCaseTaskRepository;
        this.json = json;
        this.knowledgeExportExecutor = knowledgeExportExecutor;
        this.exportDirectory = Path.of(System.getProperty("java.io.tmpdir"), "medkernel-knowledge-exports");
    }

    /**
     * 提交异步导出作业。立即返回 PENDING；调用方需轮询 {@link #get(String)} 或订阅事件。
     */
    @Transactional
    public KnowledgeExportJob submit(ExportType type, String filterJson) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        Instant now = Instant.now();
        KnowledgeExportJob job = new KnowledgeExportJob(
            null, tenantId,
            UUID.randomUUID().toString(),
            actor, type, filterJson,
            ExportStatus.PENDING, 0,
            null, null, null,
            now, null, null, null
        );
        KnowledgeExportJob saved = jobRepository.save(job);
        // 等提交事务成功后再投递 worker；snapshot 让 worker 在线程池中恢复租户上下文。
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        dispatchAfterCommit(saved.jobCode(), snapshot);
        return saved;
    }

    public KnowledgeExportJob get(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("导出作业 jobCode=" + jobCode));
    }

    public List<KnowledgeExportJob> listRecent() {
        String tenantId = requireCurrentTenant();
        return jobRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public KnowledgeExportJob cancel(String jobCode) {
        KnowledgeExportJob job = get(jobCode);
        if (job.isTerminal()) {
            throw new ApiException(ErrorCode.CONFLICT, "作业已终态（" + job.status() + "），无法取消");
        }
        return updateStatus(job, ExportStatus.CANCELLED, null, null, "用户取消");
    }

    // ─── 内部 worker ────────────────────────────────────────

    private void dispatchAfterCommit(String jobCode, RequestContext.Snapshot snapshot) {
        Runnable worker = () -> RequestContext.runWith(snapshot, () -> {
            try {
                executeJob(jobCode);
            } catch (Exception e) {
                log.error("Knowledge export job {} failed", jobCode, e);
                markFailed(jobCode, e.getMessage());
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeExportExecutor.execute(worker);
                }
            });
        } else {
            knowledgeExportExecutor.execute(worker);
        }
    }

    void executeJob(String jobCode) throws IOException {
        String tenantId = requireCurrentTenant();
        KnowledgeExportJob job = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> new IllegalStateException("Job " + jobCode + " missing in worker"));
        if (job.status() != ExportStatus.PENDING) {
            log.warn("Skip job {} in status {}", jobCode, job.status());
            return;
        }
        Instant startedAt = Instant.now();
        jobRepository.save(rebuild(job, b -> {
            b.status = ExportStatus.RUNNING;
            b.startedAt = startedAt;
            b.progress = 10;
        }));

        ExportFile exportFile = writeExportFile(tenantId, job);

        Instant completedAt = Instant.now();
        Instant expiresAt = completedAt.plus(DEFAULT_TTL);
        KnowledgeExportJob refreshed = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode).orElseThrow();
        jobRepository.save(rebuild(refreshed, b -> {
            b.status = ExportStatus.SUCCEEDED;
            b.startedAt = startedAt;
            b.completedAt = completedAt;
            b.progress = 100;
            b.itemCount = exportFile.itemCount();
            b.resultUri = exportFile.downloadUri();
            b.expiresAt = expiresAt;
        }));
        log.info("Knowledge export job {} succeeded (type={}, count={}, file={})",
            jobCode, job.exportType(), exportFile.itemCount(), exportFile.path());
    }

    public InputStream downloadFile(String jobCode) throws IOException {
        KnowledgeExportJob job = get(jobCode);
        if (job.status() != ExportStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.CONFLICT, "导出作业尚未成功，当前状态=" + job.status());
        }
        Path path = physicalExportPath(jobCode);
        if (!Files.exists(path)) {
            throw ApiException.notFound("导出文件不存在或已清理 jobCode=" + jobCode);
        }
        return Files.newInputStream(path);
    }

    @Transactional
    void markFailed(String jobCode, String errorMessage) {
        String tenantId = requireCurrentTenant();
        jobRepository.findByTenantIdAndJobCode(tenantId, jobCode).ifPresent(job ->
            jobRepository.save(rebuild(job, b -> {
                b.status = ExportStatus.FAILED;
                if (b.startedAt == null) b.startedAt = Instant.now();
                b.completedAt = Instant.now();
                b.errorMessage = errorMessage;
            }))
        );
    }

    private KnowledgeExportJob updateStatus(KnowledgeExportJob job, ExportStatus newStatus,
                                            Instant startedAt, Instant completedAt, String errorMessage) {
        Instant effStarted = startedAt == null ? job.startedAt() : startedAt;
        Instant effCompleted = completedAt == null ? Instant.now() : completedAt;
        return jobRepository.save(rebuild(job, b -> {
            b.status = newStatus;
            b.startedAt = effStarted;
            b.completedAt = effCompleted;
            b.errorMessage = errorMessage;
        }));
    }

    private ExportFile writeExportFile(String tenantId, KnowledgeExportJob job) throws IOException {
        Files.createDirectories(exportDirectory);
        Path path = physicalExportPath(job.jobCode());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            long itemCount = switch (job.exportType()) {
                case IDENTITIES -> writeIdentities(writer, tenantId);
                case VERSIONS -> writeVersions(writer, tenantId);
                case LINEAGE -> writeLineage(writer, tenantId);
                case CITATIONS -> writeCitations(writer, tenantId);
                case FULL_TENANT -> writeFullTenant(writer, tenantId);
            };
            return new ExportFile(path, DOWNLOAD_PREFIX + job.jobCode() + "/download", itemCount);
        }
    }

    private long writeIdentities(BufferedWriter writer, String tenantId) throws IOException {
        return writePaged(writer, "knowledge_identity",
            (offset, limit) -> identityRepository.pageByTenantId(tenantId, offset, limit));
    }

    private long writeVersions(BufferedWriter writer, String tenantId) throws IOException {
        return writePaged(writer, "knowledge_asset_version",
            (offset, limit) -> versionRepository.pageByTenantId(tenantId, offset, limit));
    }

    private long writeLineage(BufferedWriter writer, String tenantId) throws IOException {
        long count = writeIdentities(writer, tenantId);
        count += writeVersions(writer, tenantId);
        count += writePaged(writer, "knowledge_supersession",
            (offset, limit) -> supersessionRepository.pageByTenantId(tenantId, offset, limit));
        count += writeInvalidations(writer, tenantId);
        count += writeAffectedCaseTasks(writer, tenantId);
        return count;
    }

    private long writeCitations(BufferedWriter writer, String tenantId) throws IOException {
        return writePaged(writer, "citation",
            (offset, limit) -> citationRepository.pageByTenantId(tenantId, offset, limit));
    }

    private long writeFullTenant(BufferedWriter writer, String tenantId) throws IOException {
        return writeLineage(writer, tenantId) + writeCitations(writer, tenantId);
    }

    private long writeInvalidations(BufferedWriter writer, String tenantId) throws IOException {
        return writePaged(writer, "knowledge_invalidation",
            (offset, limit) -> invalidationRepository.pageByTenantId(tenantId, offset, limit));
    }

    private long writeAffectedCaseTasks(BufferedWriter writer, String tenantId) throws IOException {
        return writePaged(writer, "affected_case_task",
            (offset, limit) -> affectedCaseTaskRepository.pageByTenantId(tenantId, offset, limit));
    }

    private <T> long writePaged(BufferedWriter writer, String recordType, PageFetcher<T> fetcher) throws IOException {
        long count = 0;
        int offset = 0;
        while (true) {
            List<T> rows = fetcher.fetch(offset, EXPORT_BATCH_SIZE);
            if (rows.isEmpty()) {
                return count;
            }
            for (T row : rows) {
                writeJsonLine(writer, recordType, row);
                count++;
            }
            if (rows.size() < EXPORT_BATCH_SIZE) {
                return count;
            }
            offset += rows.size();
        }
    }

    private void writeJsonLine(BufferedWriter writer, String recordType, Object payload) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("recordType", recordType);
        line.put("payload", payload);
        writer.write(json.writeValueAsString(line));
        writer.newLine();
    }

    private Path physicalExportPath(String jobCode) {
        String safeJobCode = jobCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        return exportDirectory.resolve("knowledge-export-" + safeJobCode + ".jsonl");
    }

    Path physicalExportPathForTest(String jobCode) {
        return physicalExportPath(jobCode);
    }

    /**
     * 用 mutator 在 record 上做"字段拷贝 + 局部修改"，避免每次都拼 14 个参数。
     */
    private static KnowledgeExportJob rebuild(KnowledgeExportJob src, java.util.function.Consumer<JobBuilder> mutator) {
        JobBuilder b = new JobBuilder(src);
        mutator.accept(b);
        return new KnowledgeExportJob(
            b.id, b.tenantId, b.jobCode, b.requestedBy,
            b.exportType, b.filterJson,
            b.status, b.progress,
            b.resultUri, b.itemCount, b.errorMessage,
            b.createdAt, b.startedAt, b.completedAt, b.expiresAt
        );
    }

    private static final class JobBuilder {
        Long id;
        String tenantId;
        String jobCode;
        String requestedBy;
        ExportType exportType;
        String filterJson;
        ExportStatus status;
        Integer progress;
        String resultUri;
        Long itemCount;
        String errorMessage;
        Instant createdAt;
        Instant startedAt;
        Instant completedAt;
        Instant expiresAt;

        JobBuilder(KnowledgeExportJob j) {
            this.id = j.id();
            this.tenantId = j.tenantId();
            this.jobCode = j.jobCode();
            this.requestedBy = j.requestedBy();
            this.exportType = j.exportType();
            this.filterJson = j.filterJson();
            this.status = j.status();
            this.progress = j.progress();
            this.resultUri = j.resultUri();
            this.itemCount = j.itemCount();
            this.errorMessage = j.errorMessage();
            this.createdAt = j.createdAt();
            this.startedAt = j.startedAt();
            this.completedAt = j.completedAt();
            this.expiresAt = j.expiresAt();
        }
    }

    private record ExportFile(Path path, String downloadUri, long itemCount) {
    }

    @FunctionalInterface
    private interface PageFetcher<T> {
        List<T> fetch(int offset, int limit);
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
            .filter(s -> !s.isBlank())
            .orElse("system");
    }
}
