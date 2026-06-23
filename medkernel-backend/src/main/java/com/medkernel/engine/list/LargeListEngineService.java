package com.medkernel.engine.list;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.PageQuery;
import com.medkernel.shared.api.PageResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditActorClassifier;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.audit.persistence.AuditEventQuery;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.audit.persistence.AuditEventRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.export.ExportConfirmationGate;
import com.medkernel.shared.export.ExportCompletionRequested;
import com.medkernel.shared.export.ExportArtifact;
import com.medkernel.shared.export.ExportArtifactProvider;

/**
 * 大规模数据列表检索与异步批量导出核心服务引擎。
 *
 * <p>提供高性能的列表检索（含游标分页、Total Estimate 行数近似优化）以及分批异步 CSV 导出。
 * 作为 {@link ExportArtifactProvider} 向导出确认服务提供 AUDIT_EVENT / TERMINOLOGY_MAPPING 完成产物。
 */
@Service
public class LargeListEngineService implements ExportArtifactProvider {

    private static final Logger log = LoggerFactory.getLogger(LargeListEngineService.class);

    private final LargeListExportJobRepository jobRepository;
    private final AuditEventRepository auditRepository;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final JdbcTemplate jdbc;
    private final Executor knowledgeExportExecutor;
    private final ObjectMapper objectMapper;
    private final SmCryptoService crypto;
    private final ExportConfirmationGate confirmationGate;
    private final ApplicationEventPublisher applicationEventPublisher;

    public LargeListEngineService(
        LargeListExportJobRepository jobRepository,
        AuditEventRepository auditRepository,
        AuditEventPublisher auditPublisher,
        IsolatedAuditPublisher isolatedAudit,
        JdbcTemplate jdbc,
        @Qualifier("knowledgeExportExecutor") Executor knowledgeExportExecutor,
        SmCryptoService crypto,
        ExportConfirmationGate confirmationGate,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.jobRepository = jobRepository;
        this.auditRepository = auditRepository;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
        this.jdbc = jdbc;
        this.knowledgeExportExecutor = knowledgeExportExecutor;
        this.objectMapper = new ObjectMapper();
        this.crypto = crypto;
        this.confirmationGate = confirmationGate;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 高性能大规模列表检索。
     *
     * <p>支持解析 Base64 主键物理游标，以规避深分页导致的慢 SQL 问题。
     * 同时支持 Total Estimate 近似总行数估算，以防止全表 Count 锁表。
     *
     * @param request 列表查询入参
     * @return 统一列表检索出参
     */
    @Transactional(readOnly = true)
    public PageResult<AuditEventRecord> queryAuditEvents(PageQuery request) {
        String tenantId = requireCurrentTenant();
        PageQuery norm = request == null ? PageQuery.first() : request;
        int pageSize = norm.validatedSize();
        LargeListResourceDefinition definition = LargeListResourceDefinition.auditEvents();
        LargeListResourceDefinition.SortSpec sort = definition.validateSort(norm.sort());
        Map<String, String> filters = definition.validateFilters(norm.filters());
        Long cursorId = decodeCursor(norm.cursor());

        AuditEventQuery query = new AuditEventQuery(
            filterValue(filters, "action"),
            filterValue(filters, "resourceType"),
            filterValue(filters, "actorUserId"),
            filterValue(filters, "traceId"),
            filterValue(filters, "orgPathPrefix"),
            filterValue(filters, "environmentKey"),
            filterValue(filters, "outcome"),
            Boolean.parseBoolean(filterValue(filters, "superAdminOnly")),
            parseInstantFilter(filters, "from"),
            parseInstantFilter(filters, "to"),
            cursorId,
            pageSize,
            norm.safeOffset(),
            sort.field(),
            sort.direction()
        );

        List<AuditEventRecord> rows = auditRepository.findPage(tenantId, query);

        boolean hasMore = false;
        String nextCursor = null;
        List<AuditEventRecord> records = rows;

        if (rows.size() > pageSize) {
            hasMore = true;
            records = rows.subList(0, pageSize);
            AuditEventRecord last = records.get(records.size() - 1);
            nextCursor = encodeCursor(last.id());
        }

        long knownMinimum = norm.safeOffset() + records.size() + (hasMore ? 1L : 0L);
        long totalEstimate = estimateCount(tenantId, filters, knownMinimum);

        return new PageResult<>(records, nextCursor, totalEstimate, true, hasMore);
    }

    /**
     * 近似总行数估算，使用 SQL 标准 FETCH FIRST 限制以兼容 PostgreSQL 与 Oracle。
     * 计数不可用时返回当前分页结果能够证明的下界，不将未知总数误报为零。
     */
    private long estimateCount(String tenantId, Map<String, String> filters, long knownMinimum) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM (SELECT 1 FROM audit_event WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);

        String action = filterValue(filters, "action");
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action);
        }
        String resourceType = filterValue(filters, "resourceType");
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource_type = ?");
            params.add(resourceType);
        }
        String actor = filterValue(filters, "actorUserId");
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND actor_user_id = ?");
            params.add(actor);
        }
        String traceId = filterValue(filters, "traceId");
        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND trace_id = ?");
            params.add(traceId);
        }
        String orgPathPrefix = filterValue(filters, "orgPathPrefix");
        if (orgPathPrefix != null) {
            sql.append(" AND (org_path = ? OR org_path LIKE ?)");
            params.add(orgPathPrefix);
            params.add(orgPathPrefix + "/%");
        }
        String environmentKey = filterValue(filters, "environmentKey");
        if (environmentKey != null) {
            sql.append(" AND environment_key = ?");
            params.add(environmentKey);
        }
        String outcome = filterValue(filters, "outcome");
        if (outcome != null) {
            sql.append(" AND outcome = ?");
            params.add(outcome);
        }
        Instant from = parseInstantFilter(filters, "from");
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(java.sql.Timestamp.from(from));
        }
        Instant to = parseInstantFilter(filters, "to");
        if (to != null) {
            sql.append(" AND occurred_at < ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (Boolean.parseBoolean(filterValue(filters, "superAdminOnly"))) {
            appendSuperAdminRoleFilter(sql, params);
        }
        sql.append(" FETCH FIRST 10001 ROWS ONLY) t");

        try {
            Long val = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
            long count = val == null ? knownMinimum : Math.max(val, knownMinimum);
            return Math.min(10000L, count);
        } catch (Exception e) {
            log.warn("总数估算失败，返回当前分页结果的已知下界: {}", knownMinimum, e);
            return Math.min(10000L, knownMinimum);
        }
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(decoded);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的 Base64 列表游标格式");
        }
    }

    private String encodeCursor(Long id) {
        return Base64.getEncoder().encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    private Instant parseInstantFilter(Map<String, String> filters, String key) {
        String value = filterValue(filters, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "时间过滤条件必须使用 ISO-8601 格式: " + key);
        }
    }

    private void appendSuperAdminRoleFilter(StringBuilder sql, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        for (String role : AuditActorClassifier.superAdminRoles()) {
            conditions.add("actor_roles = ?");
            params.add(role);
            conditions.add("actor_roles LIKE ?");
            params.add(role + ",%");
            conditions.add("actor_roles LIKE ?");
            params.add("%," + role + ",%");
            conditions.add("actor_roles LIKE ?");
            params.add("%," + role);
        }
        sql.append(" AND (").append(String.join(" OR ", conditions)).append(")");
    }

    /**
     * 提交异步大规模列表批量导出任务。
     *
     * @param request 异步导出请求参数
     * @return 导出任务提交回执
     */
    @Transactional
    public ExportSubmitResponse submitExportTask(ExportSubmitRequest request) {
        String tenantId = requireCurrentTenant();
        String jobId = UUID.randomUUID().toString();
        String traceId = RequestContext.currentTraceId();
        String creator = currentActor();

        String resourceType = normalizeExportResource(request.resourceType());
        Map<String, String> validatedFilters = exportDefinition(resourceType).validateFilters(request.filters());
        ExportSubmitRequest normalizedRequest = new ExportSubmitRequest(
            resourceType,
            validatedFilters,
            request.selectedScope(),
            request.idempotencyKey(),
            request.confirmationId()
        );

        if (!"CURRENT_PAGE".equals(normalizedRequest.selectedScope())
            && !"FILTERED_RESULT".equals(normalizedRequest.selectedScope())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出范围仅支持 CURRENT_PAGE 或 FILTERED_RESULT");
        }

        String requestSnapshot = buildRequestSnapshot(resourceType, normalizedRequest);
        String confirmationId = requireConfirmationId(normalizedRequest.confirmationId());
        confirmationGate.requireConfirmedForExport(
            tenantId,
            confirmationId,
            resourceType,
            requestSnapshot
        );
        ExportSubmitResponse existingResponse =
            reuseExistingIdempotentJob(tenantId, normalizedRequest, requestSnapshot);
        if (existingResponse != null) {
            return existingResponse;
        }

        LargeListExportJob job = LargeListExportJob.createPending(
            jobId,
            tenantId,
            resourceType,
            requestSnapshot,
            normalizedRequest.selectedScope(),
            traceId,
            normalizedRequest.idempotencyKey(),
            creator
        );

        LargeListExportJob saved = jobRepository.save(job);
        log.info("Successfully submitted large list export job, jobId={}, tenantId={}", jobId, tenantId);

        // 传递当前请求上下文快照给后台异步线程
        RequestContext.Snapshot snapshot = RequestContext.snapshot();

        Runnable worker = () -> RequestContext.runWith(snapshot, () -> {
            try {
                executeExport(jobId);
            } catch (Exception e) {
                log.error("Failed to execute large list export job: {}", jobId, e);
                markExportFailed(jobId, e.getMessage());
            }
        });

        // 注册事务提交流程以防止幻读或数据未完全落库
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

        return new ExportSubmitResponse(saved.jobId(), "PENDING", "导出任务已提交后台处理");
    }

    private ExportSubmitResponse reuseExistingIdempotentJob(
        String tenantId,
        ExportSubmitRequest request,
        String requestSnapshot
    ) {
        if (request.idempotencyKey() == null) {
            return null;
        }
        return jobRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
            .map(existing -> {
                if (!sameRequestSnapshot(existing.requestSnapshot(), requestSnapshot)) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "同一幂等键不能用于不同导出请求");
                }
                return new ExportSubmitResponse(existing.jobId(), existing.status(), "导出任务已存在，复用幂等结果");
            })
            .orElse(null);
    }

    private boolean sameRequestSnapshot(String existingSnapshot, String requestSnapshot) {
        try {
            return objectMapper.readTree(existingSnapshot).equals(objectMapper.readTree(requestSnapshot));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出任务请求快照不是合法 JSON", e);
        }
    }

    /**
     * 根据 jobId 获取异步导出作业元数据。
     *
     * @param jobId 任务唯一ID
     * @return 任务详情
     */
    public LargeListExportJob getExportJob(String jobId) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByJobId(jobId)
            .filter(j -> j.tenantId().equals(tenantId))
            .orElseThrow(() -> ApiException.notFound("指定的异步导出任务不存在: " + jobId));
    }

    /**
     * 返回已完成导出任务的可信产物信息，由服务器读取真实文件并计算 SM3 摘要。
     */
    @Override
    public boolean supports(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        return "audit_event".equals(normalized) || "terminology_mapping".equals(normalized);
    }

    @Override
    public ExportArtifact completedExportArtifact(String jobId) {
        LargeListExportJob job = getExportJob(jobId);
        if (!"SUCCESS".equals(job.status())) {
            throw new ApiException(ErrorCode.ENG_LIST_003, "导出任务尚未成功，不能登记导出产物");
        }
        if (job.filePath() == null || job.filePath().isBlank()) {
            throw new ApiException(ErrorCode.ENG_LIST_004, "导出任务没有真实物理文件");
        }
        Path path = Path.of(job.filePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new ApiException(ErrorCode.ENG_LIST_004, "导出的物理 CSV 文件不存在或已被清理");
        }
        try (InputStream input = Files.newInputStream(path)) {
            return new ExportArtifact(
                job.jobId(),
                job.resourceType(),
                job.requestSnapshot(),
                job.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/" + job.jobId() + "/download",
                "sm3:" + crypto.sm3Hex(input)
            );
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.ENG_LIST_004, "导出文件摘要计算失败", exception);
        }
    }

    /**
     * 后台线程实际执行大规模列表数据的分批拉取与 CSV 文件物理生成。
     */
    void executeExport(String jobId) throws IOException {
        String tenantId = requireCurrentTenant();
        LargeListExportJob job = jobRepository.findByJobId(jobId)
            .filter(j -> j.tenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalStateException("导出任务不存在，jobId=" + jobId));

        if (!"PENDING".equals(job.status())) {
            log.warn("Job {} status is {}, skip running", jobId, job.status());
            return;
        }

        Instant startedAt = Instant.now();

        // 变更状态为 RUNNING
        updateJobStatus(job, "RUNNING", null, null, 0L, null);

        Map<String, String> filterMap = extractFilters(job.requestSnapshot());

        // 生成本地临时文件
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "medkernel-exports");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File csvFile = new File(tempDir, "export-" + jobId + ".csv");

        long count = 0;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            writer.write("\uFEFF"); // 写入 UTF-8 BOM，防止 Excel 乱码
            if ("AUDIT_EVENT".equals(job.resourceType())) {
                count = writeAuditEventExport(writer, tenantId, filterMap);
            } else if ("TERMINOLOGY_MAPPING".equals(job.resourceType())) {
                count = writeTerminologyMappingExport(writer, tenantId, filterMap);
            } else {
                throw new ApiException(ErrorCode.ENG_LIST_001, "不支持的异步导出资源类型: " + job.resourceType());
            }
            writer.flush();
        }

        Instant completedAt = Instant.now();
        long costMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

        // 成功状态变更并记录物理文件路径及大小
        updateJobStatus(job, "SUCCESS", csvFile.getName(), csvFile.getAbsolutePath(), csvFile.length(), costMs);
        applicationEventPublisher.publishEvent(new ExportCompletionRequested(
            tenantId,
            job.idempotencyKey(),
            job.jobId(),
            "后台异步导出任务已生成真实文件",
            job.createdBy()
        ));

        // 发布成功物理审计事件
        auditPublisher.publish(AuditEvent.of(
            AuditAction.EXPORT,
            "large_list_export",
            jobId,
            "异步导出大规模列表数据至 CSV 成功，共 " + count + " 条记录，耗时 " + costMs + "ms"
        ));

        log.info("Successfully completed list export job: {}, total entries={}", jobId, count);
    }

    /**
     * 标记导出任务为失败。
     */
    void markExportFailed(String jobId, String errorMsg) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            updateJobStatus(job, "FAILED", null, null, 0L, null);
            // 写入失败时包含的具体堆栈错误描述
            LargeListExportJob refreshed = jobRepository.findByJobId(jobId).orElse(job);
            jobRepository.save(new LargeListExportJob(
                refreshed.jobId(),
                refreshed.tenantId(),
                refreshed.resourceType(),
                refreshed.requestSnapshot(),
                refreshed.selectedScope(),
                "FAILED",
                null,
                null,
                0L,
                errorMsg == null ? "未知异常" : errorMsg.substring(0, Math.min(errorMsg.length(), 500)),
                refreshed.timeCostMs(),
                refreshed.traceId(),
                refreshed.auditId(),
                refreshed.idempotencyKey(),
                refreshed.createdAt(),
                refreshed.createdBy(),
                Instant.now(),
                refreshed.updatedBy()
            ));

            // 通过物理子事务发布失败审计记录
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.EXPORT,
                "large_list_export",
                jobId,
                ErrorCode.ENG_LIST_004.code(),
                "大规模列表 CSV 后台导出失败: " + errorMsg
            ));
        });
    }

    /**
     * 获取物理 CSV 文件的输入流以便 Controller 输出下载。
     *
     * @param jobId 任务唯一ID
     * @return 文件的物理输入流
     */
    public FileInputStream downloadFile(String jobId) {
        LargeListExportJob job = getExportJob(jobId);

        if (!"SUCCESS".equals(job.status())) {
            if ("FAILED".equals(job.status())) {
                throw new ApiException(ErrorCode.ENG_LIST_004, "导出任务执行失败，无法下载: " + job.errorMessage());
            }
            throw new ApiException(ErrorCode.ENG_LIST_003, "导出任务尚未完成，无法提供物理下载，当前状态: " + job.status());
        }

        File file = new File(job.filePath());
        if (!file.exists()) {
            throw new ApiException(ErrorCode.ENG_LIST_004, "导出的物理 CSV 文件在服务器上不存在或已被清理");
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            
            // 记录成功下载的物理审计事件
            auditPublisher.publish(AuditEvent.of(
                AuditAction.EXPORT,
                "large_list_export",
                jobId,
                "用户成功下载异步导出文件: " + job.fileName()
            ));

            return fis;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ENG_LIST_004, "物理文件读取失败: " + e.getMessage());
        }
    }

    private void updateJobStatus(LargeListExportJob src, String newStatus, String fileName, String filePath, Long fileSize, Long costMs) {
        LargeListExportJob refreshed = jobRepository.findByJobId(src.jobId()).orElse(src);
        LargeListExportJob updated = new LargeListExportJob(
            refreshed.jobId(),
            refreshed.tenantId(),
            refreshed.resourceType(),
            refreshed.requestSnapshot(),
            refreshed.selectedScope(),
            newStatus,
            fileName == null ? refreshed.fileName() : fileName,
            filePath == null ? refreshed.filePath() : filePath,
            fileSize == null ? refreshed.fileSize() : fileSize,
            refreshed.errorMessage(),
            costMs == null ? refreshed.timeCostMs() : costMs,
            refreshed.traceId(),
            refreshed.auditId(),
            refreshed.idempotencyKey(),
            refreshed.createdAt(),
            refreshed.createdBy(),
            Instant.now(),
            refreshed.updatedBy()
        );
        jobRepository.save(updated);
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

    private String normalizeExportResource(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toUpperCase();
        if (!"AUDIT_EVENT".equals(normalized) && !"TERMINOLOGY_MAPPING".equals(normalized)) {
            throw new ApiException(ErrorCode.ENG_LIST_001, "不支持的异步导出资源类型: " + resourceType);
        }
        return normalized;
    }

    private String requireConfirmationId(String confirmationId) {
        if (confirmationId == null || confirmationId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出确认 ID 不能为空");
        }
        return confirmationId.trim();
    }

    private LargeListResourceDefinition exportDefinition(String resourceType) {
        return switch (resourceType) {
            case "AUDIT_EVENT" -> LargeListResourceDefinition.auditEvents();
            case "TERMINOLOGY_MAPPING" -> LargeListResourceDefinition.terminologyMappings();
            default -> throw new ApiException(
                ErrorCode.ENG_LIST_001,
                "不支持的异步导出资源类型: " + resourceType
            );
        };
    }

    private String buildRequestSnapshot(String resourceType, ExportSubmitRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resourceType", resourceType);
        snapshot.put("filters", request.filters());
        snapshot.put("selectedScope", request.selectedScope());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出请求快照无法序列化", e);
        }
    }

    private Map<String, String> extractFilters(String requestSnapshot) {
        try {
            JsonNode filters = objectMapper.readTree(requestSnapshot).path("filters");
            if (!filters.isObject()) {
                return Map.of();
            }
            Map<String, String> parsed = new LinkedHashMap<>();
            filters.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                    parsed.put(entry.getKey(), value.asText());
                }
            });
            return parsed;
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出任务请求快照不是合法 JSON", e);
        }
    }

    private String filterValue(Map<String, String> filters, String key) {
        String value = filters.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private long writeAuditEventExport(
        BufferedWriter writer,
        String tenantId,
        Map<String, String> filters
    ) throws IOException {
        writer.write("自增ID,事件ID,追踪ID,发生时间,操作人ID,操作动作,资源类型,资源ID,摘要,outcome,错误码\n");

        Long cursorId = null;
        boolean hasNext = true;
        int batchSize = 500;
        long count = 0;

        while (hasNext) {
            AuditEventQuery query = new AuditEventQuery(
                filterValue(filters, "action"),
                filterValue(filters, "resourceType"),
                filterValue(filters, "actorUserId"),
                filterValue(filters, "traceId"),
                filterValue(filters, "orgPathPrefix"),
                filterValue(filters, "environmentKey"),
                filterValue(filters, "outcome"),
                Boolean.parseBoolean(filterValue(filters, "superAdminOnly")),
                parseInstantFilter(filters, "from"),
                parseInstantFilter(filters, "to"),
                cursorId,
                batchSize
            );

            List<AuditEventRecord> rows = auditRepository.findPage(tenantId, query);
            if (rows.isEmpty()) {
                break;
            }

            List<AuditEventRecord> batchList = rows;
            if (rows.size() > batchSize) {
                batchList = rows.subList(0, batchSize);
                cursorId = batchList.get(batchList.size() - 1).id();
            } else {
                hasNext = false;
            }

            for (AuditEventRecord row : batchList) {
                writer.write(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    row.id(),
                    escapeCsv(row.eventId()),
                    escapeCsv(row.traceId()),
                    row.occurredAt() == null ? "" : row.occurredAt().toString(),
                    escapeCsv(row.actorUserId()),
                    row.action() == null ? "" : row.action(),
                    escapeCsv(row.resourceType()),
                    escapeCsv(row.resourceId()),
                    escapeCsv(row.summary()),
                    escapeCsv(row.outcome()),
                    escapeCsv(row.errorCode())
                ));
                count++;
            }
        }
        return count;
    }

    private long writeTerminologyMappingExport(
        BufferedWriter writer,
        String tenantId,
        Map<String, String> filters
    ) throws IOException {
        writer.write("映射ID,院内术语ID,标准术语ID,来源系统,类别,置信度,风险等级,状态,证据,确认人,确认时间,更新时间\n");

        long count = 0;
        int offset = 0;
        int batchSize = 500;
        while (true) {
            java.util.List<Object> params = new java.util.ArrayList<>();
            params.add(tenantId);
            String sql = terminologyMappingExportSql(filters, params, offset, batchSize);
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    escapeCsvValue(rowValue(row, "id")),
                    escapeCsvValue(rowValue(row, "local_term_id")),
                    escapeCsvValue(rowValue(row, "standard_term_id")),
                    escapeCsvValue(rowValue(row, "source_system")),
                    escapeCsvValue(rowValue(row, "category")),
                    escapeCsvValue(rowValue(row, "confidence")),
                    escapeCsvValue(rowValue(row, "risk_level")),
                    escapeCsvValue(rowValue(row, "status")),
                    escapeCsvValue(rowValue(row, "evidence_text")),
                    escapeCsvValue(rowValue(row, "confirmed_by")),
                    escapeCsvValue(rowValue(row, "confirmed_at")),
                    escapeCsvValue(rowValue(row, "updated_at"))
                ));
                count++;
            }
            if (rows.size() < batchSize) {
                break;
            }
            offset += batchSize;
        }
        return count;
    }

    private String terminologyMappingExportSql(
        Map<String, String> filters,
        java.util.List<Object> params,
        int offset,
        int batchSize
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, local_term_id, standard_term_id, source_system, category, confidence,
                   risk_level, status, evidence_text, confirmed_by, confirmed_at, updated_at
            FROM term_mapping
            WHERE tenant_id = ?
            """);
        String sourceSystem = filterValue(filters, "sourceSystem");
        if (sourceSystem != null) {
            sql.append(" AND source_system = ?");
            params.add(sourceSystem);
        }
        String category = filterValue(filters, "category");
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        String status = filterValue(filters, "status");
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        String keyword = filterValue(filters, "keyword");
        if (keyword != null) {
            sql.append(" AND LOWER(COALESCE(evidence_text, '')) LIKE ?");
            params.add("%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        sql.append(" ORDER BY updated_at DESC, id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(batchSize);
        return sql.toString();
    }

    private String escapeCsvValue(Object value) {
        return escapeCsv(value == null ? null : value.toString());
    }

    private Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        return row.get(key.toUpperCase(java.util.Locale.ROOT));
    }

    private String escapeCsv(String val) {
        if (val == null) {
            return "";
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
