package com.medkernel.engine.list;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.audit.persistence.AuditEventQuery;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.audit.persistence.AuditEventRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 大规模数据列表检索与异步批量导出核心服务引擎。
 *
 * <p>提供高性能的列表检索（含游标分页、Total Estimate 行数近似优化）以及分批异步 CSV 导出。
 */
@Service
public class LargeListEngineService {

    private static final Logger log = LoggerFactory.getLogger(LargeListEngineService.class);

    private final LargeListExportJobRepository jobRepository;
    private final AuditEventRepository auditRepository;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final JdbcTemplate jdbc;
    private final Executor knowledgeExportExecutor;
    private final ObjectMapper objectMapper;

    public LargeListEngineService(
        LargeListExportJobRepository jobRepository,
        AuditEventRepository auditRepository,
        AuditEventPublisher auditPublisher,
        IsolatedAuditPublisher isolatedAudit,
        JdbcTemplate jdbc,
        @Qualifier("knowledgeExportExecutor") Executor knowledgeExportExecutor
    ) {
        this.jobRepository = jobRepository;
        this.auditRepository = auditRepository;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
        this.jdbc = jdbc;
        this.knowledgeExportExecutor = knowledgeExportExecutor;
        this.objectMapper = new ObjectMapper();
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
    public ListQueryResponse<AuditEventRecord> queryList(ListQueryRequest request) {
        String tenantId = requireCurrentTenant();
        ListQueryRequest norm = request.normalize();

        // 仅当资源类型为 AUDIT_EVENT 或为空时允许检索
        if (!"AUDIT_EVENT".equalsIgnoreCase(norm.resourceType()) && !norm.resourceType().isBlank()) {
            throw new ApiException(ErrorCode.ENG_LIST_001, "不支持的列表检索资源类型: " + norm.resourceType());
        }

        // 解析 Base64 游标
        Long cursorId = null;
        if (norm.cursor() != null && !norm.cursor().isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(norm.cursor()));
                cursorId = Long.parseLong(decoded);
            } catch (Exception e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "非法的 Base64 列表游标格式");
            }
        }

        // 构造底座标准的审计过滤条件
        String actionFilter = norm.filters().get("action");
        String resourceTypeFilter = norm.filters().get("resourceType");
        String actorFilter = norm.filters().get("actorUserId");
        
        AuditEventQuery query = new AuditEventQuery(
            actionFilter,
            resourceTypeFilter,
            actorFilter,
            null,
            null,
            cursorId,
            norm.pageSize()
        );

        // findPage 会查出 pageSize + 1 条，以便判断 hasMore
        List<AuditEventRecord> rows = auditRepository.findPage(tenantId, query);

        boolean hasMore = false;
        String nextCursor = null;
        List<AuditEventRecord> records = rows;

        if (rows.size() > norm.pageSize()) {
            hasMore = true;
            records = rows.subList(0, norm.pageSize());
            // 取最后一条的实际物理 ID 编码为游标
            AuditEventRecord last = records.get(records.size() - 1);
            nextCursor = Base64.getEncoder().encodeToString(String.valueOf(last.id()).getBytes());
        }

        // 计算 Total Estimate 近似总行数 (限流 10000 条以防 count(*) 全表扫描)
        long totalEstimate = estimateCount(tenantId, actionFilter, resourceTypeFilter, actorFilter);

        return new ListQueryResponse<>(nextCursor, records, totalEstimate, hasMore);
    }

    /**
     * 近似总行数估算，使用 SQL 标准 FETCH FIRST 限制以兼容 PostgreSQL 与 Oracle。
     */
    private long estimateCount(String tenantId, String action, String resourceType, String actor) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM (SELECT 1 FROM audit_event WHERE tenant_id = ?");
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);

        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource_type = ?");
            params.add(resourceType);
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND actor_user_id = ?");
            params.add(actor);
        }
        
        // 限制最多 Count 至 10001 条。
        sql.append(" FETCH FIRST 10001 ROWS ONLY) t");

        try {
            Long val = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
            long count = val == null ? 0L : val;
            return count > 10000 ? 10000L : count;
        } catch (Exception e) {
            log.warn("Total estimate count failed, fallback to 0", e);
            return 0L;
        }
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

        if (!"CURRENT_PAGE".equals(request.selectedScope()) && !"FILTERED_RESULT".equals(request.selectedScope())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "导出范围仅支持 CURRENT_PAGE 或 FILTERED_RESULT");
        }

        String requestSnapshot = buildRequestSnapshot(resourceType, request);
        ExportSubmitResponse existingResponse = reuseExistingIdempotentJob(tenantId, request, requestSnapshot);
        if (existingResponse != null) {
            return existingResponse;
        }

        LargeListExportJob job = LargeListExportJob.createPending(
            jobId,
            tenantId,
            resourceType,
            requestSnapshot,
            request.selectedScope(),
            traceId,
            request.idempotencyKey(),
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
        String actionFilter = filterValue(filterMap, "action");
        String resourceTypeFilter = filterValue(filterMap, "resourceType");
        String actorFilter = filterValue(filterMap, "actorUserId");

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
                count = writeAuditEventExport(writer, tenantId, actionFilter, resourceTypeFilter, actorFilter);
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
        String actionFilter,
        String resourceTypeFilter,
        String actorFilter
    ) throws IOException {
        writer.write("自增ID,事件ID,追踪ID,发生时间,操作人ID,操作动作,资源类型,资源ID,摘要,outcome,错误码\n");

        Long cursorId = null;
        boolean hasNext = true;
        int batchSize = 500;
        long count = 0;

        while (hasNext) {
            AuditEventQuery query = new AuditEventQuery(
                actionFilter,
                resourceTypeFilter,
                actorFilter,
                null,
                null,
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
