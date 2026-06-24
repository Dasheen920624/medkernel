package com.medkernel.engine.datasvc.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medkernel.engine.datasvc.ClinicalSignalStat;
import com.medkernel.engine.datasvc.ClinicalSignalsRepository;
import com.medkernel.engine.datasvc.KnowledgeUsageStat;
import com.medkernel.engine.datasvc.KnowledgeUsageStatsRepository;
import com.medkernel.engine.datasvc.RuleUsageStat;
import com.medkernel.engine.datasvc.RuleUsageStatsRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.export.ExportConfirmationGate;
import com.medkernel.shared.export.ExportArtifact;
import com.medkernel.shared.export.ExportArtifactProvider;

/**
 * 引擎数据服务层异步导出服务（DATASVC-01，FR-1 异步导出 / FR-3 脱敏与导出限制 / FR-6 全审计）。
 *
 * <p>把三组去标识聚合读模型（规则、知识、临床信号使用统计）经导出确认门禁控制后异步导出为 CSV：
 * <ul>
 *   <li>{@code submit}：校验导出确认（资源类型 + 范围一致）→ 写 PENDING + 事务提交后投递 worker；幂等键去重。</li>
 *   <li>{@code executeJob}：worker PENDING → RUNNING → 分页拉读模型 + 小样本抑制 + 写 CSV → SUCCEEDED；上游不可用诚实 FAILED。</li>
 *   <li>{@code completedExportArtifact}（{@link ExportArtifactProvider}）：按真实 CSV 文件字节计算 SM3 摘要供导出登记完成。</li>
 * </ul>
 * 作为 {@link ExportArtifactProvider} 被导出确认服务按资源类型解析；本服务只读既有读模型，不直连原始病历。
 */
@Service
public class EngineDataExportService implements ExportArtifactProvider {

    private static final Logger log = LoggerFactory.getLogger(EngineDataExportService.class);
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final int EXPORT_BATCH_SIZE = 500;
    private static final int SUPPRESS_THRESHOLD = 10;
    private static final int DEFAULT_WINDOW_DAYS = 90;
    private static final String SUPPRESSED = "suppressed";
    private static final String DOWNLOAD_PREFIX = "/api/v1/engine-data/exports/";
    private static final String AUDIT_TARGET = "mk_engine_data_export_job";

    private final EngineDataExportJobRepository jobRepository;
    private final RuleUsageStatsRepository ruleUsageRepository;
    private final KnowledgeUsageStatsRepository knowledgeUsageRepository;
    private final ClinicalSignalsRepository clinicalSignalsRepository;
    private final ExportConfirmationGate confirmationGate;
    private final SmCryptoService crypto;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper json;
    private final Executor exportExecutor;
    private final Path exportDirectory;

    public EngineDataExportService(
            EngineDataExportJobRepository jobRepository,
            RuleUsageStatsRepository ruleUsageRepository,
            KnowledgeUsageStatsRepository knowledgeUsageRepository,
            ClinicalSignalsRepository clinicalSignalsRepository,
            ExportConfirmationGate confirmationGate,
            SmCryptoService crypto,
            AuditRecorder auditRecorder,
            ObjectMapper json,
            @Qualifier("engineDataExportExecutor") Executor exportExecutor) {
        this.jobRepository = jobRepository;
        this.ruleUsageRepository = ruleUsageRepository;
        this.knowledgeUsageRepository = knowledgeUsageRepository;
        this.clinicalSignalsRepository = clinicalSignalsRepository;
        this.confirmationGate = confirmationGate;
        this.crypto = crypto;
        this.auditRecorder = auditRecorder;
        this.json = json;
        this.exportExecutor = exportExecutor;
        this.exportDirectory = Path.of(System.getProperty("java.io.tmpdir"), "medkernel-engine-data-exports");
    }

    /**
     * 提交异步导出作业。须先确认资源类型和导出范围，否则结构化拒绝。
     */
    @Transactional
    public EngineDataExportJob submit(
            EngineDataExportType type,
            int windowDays,
            String confirmationId,
            String idempotencyKey) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        String safeConfirmationId = requireText(confirmationId, "导出确认 ID 不能为空");
        String safeIdem = requireText(idempotencyKey, "导出幂等键不能为空");
        int safeWindow = windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        String requestSnapshot = buildRequestSnapshot(type, safeWindow);

        // 幂等：同租户同幂等键已存在则返回既有作业。
        Optional<EngineDataExportJob> existing = jobRepository.findByTenantIdAndIdempotencyKey(tenantId, safeIdem);
        if (existing.isPresent()) {
            return existing.get();
        }

        confirmationGate.requireConfirmedForExport(
            tenantId,
            safeConfirmationId,
            type.resourceType(),
            requestSnapshot
        );

        Instant now = Instant.now();
        EngineDataExportJob job = new EngineDataExportJob(
            null, tenantId, UUID.randomUUID().toString(), actor, type,
            ExportJobStatus.PENDING, 0, null, null, null,
            safeConfirmationId, safeIdem, requestSnapshot, now, null, null, null);
        EngineDataExportJob saved = jobRepository.save(job);
        auditRecorder.record(AuditAction.EXPORT, AUDIT_TARGET, saved.jobCode(),
            "提交引擎数据导出作业 type=" + type + " 确认=" + safeConfirmationId + " tenant=" + tenantId);
        // 等提交事务成功后再投递 worker；snapshot 让 worker 在线程池中恢复租户上下文。
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        dispatchAfterCommit(saved.jobCode(), snapshot);
        return saved;
    }

    public EngineDataExportJob get(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("导出作业 jobCode=" + jobCode));
    }

    public PageResponse<EngineDataExportJob> listRecent(PageRequest request) {
        String tenantId = requireCurrentTenant();
        PageRequest page = request == null ? PageRequest.defaults() : request;
        long total = jobRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(page);
        }
        List<EngineDataExportJob> items = jobRepository.pageByTenantId(tenantId, page.offset(), page.safeSize());
        return PageResponse.of(items, page, total);
    }

    @Transactional
    public EngineDataExportJob cancel(String jobCode) {
        EngineDataExportJob job = get(jobCode);
        if (job.isTerminal()) {
            throw new ApiException(ErrorCode.CONFLICT, "作业已终态（" + job.status() + "），无法取消");
        }
        EngineDataExportJob cancelled = rebuild(job, b -> {
            b.status = ExportJobStatus.CANCELLED;
            b.completedAt = Instant.now();
            b.errorMessage = "用户取消";
        });
        return jobRepository.save(cancelled);
    }

    public InputStream downloadFile(String jobCode) throws IOException {
        EngineDataExportJob job = get(jobCode);
        if (job.status() != ExportJobStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.CONFLICT, "导出作业尚未成功，当前状态=" + job.status());
        }
        Path path = physicalExportPath(jobCode);
        if (!Files.exists(path)) {
            throw ApiException.notFound("导出文件不存在或已清理 jobCode=" + jobCode);
        }
        return Files.newInputStream(path);
    }

    // ─── ExportArtifactProvider（导出登记完成时按资源类型解析本来源）──────────

    @Override
    public boolean supports(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        for (EngineDataExportType type : EngineDataExportType.values()) {
            if (type.resourceType().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ExportArtifact completedExportArtifact(String jobCode) {
        EngineDataExportJob job = get(jobCode);
        if (job.status() != ExportJobStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.CONFLICT, "导出作业尚未成功，不能登记导出产物");
        }
        Path path = physicalExportPath(jobCode);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("导出物理文件不存在或已被清理 jobCode=" + jobCode);
        }
        try (InputStream input = Files.newInputStream(path)) {
            return new ExportArtifact(
                job.jobCode(),
                job.exportType().resourceType(),
                job.requestSnapshot(),
                job.idempotencyKey(),
                DOWNLOAD_PREFIX + job.jobCode() + "/download",
                "sm3:" + crypto.sm3Hex(input));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出文件摘要计算失败", exception);
        }
    }

    // ─── 内部 worker ──────────────────────────────────────────

    private void dispatchAfterCommit(String jobCode, RequestContext.Snapshot snapshot) {
        Runnable worker = () -> RequestContext.runWith(snapshot, () -> {
            try {
                executeJob(jobCode);
            } catch (Exception e) {
                log.error("Engine-data export job {} failed", jobCode, e);
                markFailed(jobCode, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    exportExecutor.execute(worker);
                }
            });
        } else {
            exportExecutor.execute(worker);
        }
    }

    void executeJob(String jobCode) throws IOException {
        String tenantId = requireCurrentTenant();
        EngineDataExportJob job = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> new IllegalStateException("Job " + jobCode + " missing in worker"));
        if (job.status() != ExportJobStatus.PENDING) {
            log.warn("Skip engine-data export job {} in status {}", jobCode, job.status());
            return;
        }
        Instant startedAt = Instant.now();
        jobRepository.save(rebuild(job, b -> {
            b.status = ExportJobStatus.RUNNING;
            b.startedAt = startedAt;
            b.progress = 10;
        }));

        ExportFile exportFile = writeExportFile(tenantId, job);

        Instant completedAt = Instant.now();
        Instant expiresAt = completedAt.plus(DEFAULT_TTL);
        EngineDataExportJob refreshed = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode).orElseThrow();
        jobRepository.save(rebuild(refreshed, b -> {
            b.status = ExportJobStatus.SUCCEEDED;
            b.startedAt = startedAt;
            b.completedAt = completedAt;
            b.progress = 100;
            b.itemCount = exportFile.itemCount();
            b.resultUri = exportFile.downloadUri();
            b.expiresAt = expiresAt;
        }));
        auditRecorder.record(AuditAction.EXPORT, AUDIT_TARGET, jobCode,
            "引擎数据导出作业完成 type=" + job.exportType() + " 行数=" + exportFile.itemCount());
        log.info("Engine-data export job {} succeeded (type={}, count={})",
            jobCode, job.exportType(), exportFile.itemCount());
    }

    @Transactional
    void markFailed(String jobCode, String errorMessage) {
        String tenantId = requireCurrentTenant();
        jobRepository.findByTenantIdAndJobCode(tenantId, jobCode).ifPresent(job ->
            jobRepository.save(rebuild(job, b -> {
                b.status = ExportJobStatus.FAILED;
                if (b.startedAt == null) {
                    b.startedAt = Instant.now();
                }
                b.completedAt = Instant.now();
                b.errorMessage = errorMessage;
            })));
    }

    // ─── CSV 写入（分页 + 小样本抑制）────────────────────────────

    private ExportFile writeExportFile(String tenantId, EngineDataExportJob job) throws IOException {
        Files.createDirectories(exportDirectory);
        Path path = physicalExportPath(job.jobCode());
        int windowDays = readWindowDays(job.requestSnapshot());
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(Duration.ofDays(windowDays));
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF'); // UTF-8 BOM，便于 Excel 正确识别中文表头
            long itemCount = switch (job.exportType()) {
                case RULE_USAGE -> writeRuleUsage(writer, tenantId, windowStart, windowEnd);
                case KNOWLEDGE_USAGE -> writeKnowledgeUsage(writer, tenantId, windowStart, windowEnd);
                case CLINICAL_SIGNALS -> writeClinicalSignals(writer, tenantId, windowStart, windowEnd);
            };
            return new ExportFile(path, DOWNLOAD_PREFIX + job.jobCode() + "/download", itemCount);
        }
    }

    private long writeRuleUsage(BufferedWriter writer, String tenantId, Instant start, Instant end) throws IOException {
        writeCsv(writer, "规则ID", "执行总数", "命中数", "失败数", "最近执行时间", "已抑制");
        long count = 0;
        int offset = 0;
        while (true) {
            List<RuleUsageStat> rows = ruleUsageRepository.aggregateRuleUsage(tenantId, start, end, offset, EXPORT_BATCH_SIZE);
            if (rows.isEmpty()) {
                return count;
            }
            for (RuleUsageStat r : rows) {
                boolean suppressed = r.totalExecutions() < SUPPRESS_THRESHOLD;
                writeCsv(writer, r.ruleId(),
                    countCell(suppressed, r.totalExecutions()),
                    countCell(suppressed, r.hitCount()),
                    countCell(suppressed, r.failedCount()),
                    instant(r.lastExecutedAt()),
                    String.valueOf(suppressed));
                count++;
            }
            if (rows.size() < EXPORT_BATCH_SIZE) {
                return count;
            }
            offset += rows.size();
        }
    }

    private long writeKnowledgeUsage(BufferedWriter writer, String tenantId, Instant start, Instant end) throws IOException {
        writeCsv(writer, "知识引用键", "知识标题", "被引用次数", "去重卡片数", "最近使用时间", "已抑制");
        long count = 0;
        int offset = 0;
        while (true) {
            List<KnowledgeUsageStat> rows = knowledgeUsageRepository.aggregateKnowledgeUsage(tenantId, start, end, offset, EXPORT_BATCH_SIZE);
            if (rows.isEmpty()) {
                return count;
            }
            for (KnowledgeUsageStat k : rows) {
                boolean suppressed = k.citationCount() < SUPPRESS_THRESHOLD;
                writeCsv(writer, k.knowledgeRefId(), k.knowledgeTitle(),
                    countCell(suppressed, k.citationCount()),
                    countCell(suppressed, k.distinctCardCount()),
                    instant(k.lastUsedAt()),
                    String.valueOf(suppressed));
                count++;
            }
            if (rows.size() < EXPORT_BATCH_SIZE) {
                return count;
            }
            offset += rows.size();
        }
    }

    private long writeClinicalSignals(BufferedWriter writer, String tenantId, Instant start, Instant end) throws IOException {
        writeCsv(writer, "信号类别", "信号总数", "高危数", "采纳数", "驳回数", "最近信号时间", "已抑制");
        long count = 0;
        int offset = 0;
        while (true) {
            List<ClinicalSignalStat> rows = clinicalSignalsRepository.aggregateClinicalSignals(tenantId, start, end, offset, EXPORT_BATCH_SIZE);
            if (rows.isEmpty()) {
                return count;
            }
            for (ClinicalSignalStat c : rows) {
                boolean suppressed = c.totalSignals() < SUPPRESS_THRESHOLD;
                writeCsv(writer, c.signalType(),
                    countCell(suppressed, c.totalSignals()),
                    countCell(suppressed, c.highRiskCount()),
                    countCell(suppressed, c.acceptedCount()),
                    countCell(suppressed, c.rejectedCount()),
                    instant(c.lastSignalAt()),
                    String.valueOf(suppressed));
                count++;
            }
            if (rows.size() < EXPORT_BATCH_SIZE) {
                return count;
            }
            offset += rows.size();
        }
    }

    private String countCell(boolean suppressed, long value) {
        return suppressed ? SUPPRESSED : Long.toString(value);
    }

    private String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private void writeCsv(BufferedWriter writer, String... cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escapeCsv(cells[i]));
        }
        writer.write(line.toString());
        writer.newLine();
    }

    private String escapeCsv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ─── 范围快照 + 工具方法 ───────────────────────────────────────

    private String buildRequestSnapshot(EngineDataExportType type, int windowDays) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("exportType", type.name());
        scope.put("windowDays", windowDays);
        try {
            return json.writeValueAsString(scope);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出范围序列化失败");
        }
    }

    private int readWindowDays(String requestSnapshot) {
        try {
            JsonNode node = json.readTree(requestSnapshot == null ? "{}" : requestSnapshot);
            JsonNode window = node.get("windowDays");
            return window != null && window.isInt() && window.asInt() > 0 ? window.asInt() : DEFAULT_WINDOW_DAYS;
        } catch (JsonProcessingException exception) {
            return DEFAULT_WINDOW_DAYS;
        }
    }

    private Path physicalExportPath(String jobCode) {
        String safeJobCode = jobCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        return exportDirectory.resolve("engine-data-export-" + safeJobCode + ".csv");
    }

    Path physicalExportPathForTest(String jobCode) {
        return physicalExportPath(jobCode);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId().filter(s -> !s.isBlank()).orElse("system");
    }

    /** 用 mutator 在 record 上做"字段拷贝 + 局部修改"，避免每次拼 17 个参数。 */
    private static EngineDataExportJob rebuild(EngineDataExportJob src, java.util.function.Consumer<JobBuilder> mutator) {
        JobBuilder b = new JobBuilder(src);
        mutator.accept(b);
        return new EngineDataExportJob(
            b.id, b.tenantId, b.jobCode, b.requestedBy, b.exportType,
            b.status, b.progress, b.resultUri, b.itemCount, b.errorMessage,
            b.confirmationId, b.idempotencyKey, b.requestSnapshot,
            b.createdAt, b.startedAt, b.completedAt, b.expiresAt);
    }

    private static final class JobBuilder {
        Long id;
        String tenantId;
        String jobCode;
        String requestedBy;
        EngineDataExportType exportType;
        ExportJobStatus status;
        Integer progress;
        String resultUri;
        Long itemCount;
        String errorMessage;
        String confirmationId;
        String idempotencyKey;
        String requestSnapshot;
        Instant createdAt;
        Instant startedAt;
        Instant completedAt;
        Instant expiresAt;

        JobBuilder(EngineDataExportJob j) {
            this.id = j.id();
            this.tenantId = j.tenantId();
            this.jobCode = j.jobCode();
            this.requestedBy = j.requestedBy();
            this.exportType = j.exportType();
            this.status = j.status();
            this.progress = j.progress();
            this.resultUri = j.resultUri();
            this.itemCount = j.itemCount();
            this.errorMessage = j.errorMessage();
            this.confirmationId = j.confirmationId();
            this.idempotencyKey = j.idempotencyKey();
            this.requestSnapshot = j.requestSnapshot();
            this.createdAt = j.createdAt();
            this.startedAt = j.startedAt();
            this.completedAt = j.completedAt();
            this.expiresAt = j.expiresAt();
        }
    }

    private record ExportFile(Path path, String downloadUri, long itemCount) {
    }
}
