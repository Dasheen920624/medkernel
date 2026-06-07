package com.medkernel.engine.list;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageQuery;
import com.medkernel.shared.api.PageResult;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.datascope.DataScope;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 大规模数据列表检索与异步批量导出控制器。
 *
 * <p>通过多租户数据范围切面 {@link DataScope} 强力防范越权，并配合安全网关实施防刷及审计控制。
 */
@RestController
@RequestMapping("/api/v1/large-lists")
@DataScope(requireTenant = true)
public class LargeListController {

    private final LargeListEngineService service;

    public LargeListController(LargeListEngineService service) {
        this.service = service;
    }

    /**
     * 高性能统一列表检索端点。
     *
     * @param request 检索过滤参数及游标定义
     * @return 列表分页记录、近似总行数及下一页游标回执
     */
    @GetMapping("/audit-events/list")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<PageResult<LargeAuditEventRow>> auditEvents(
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "offset", required = false) Long offset,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam Map<String, String> params
    ) {
        PageResult<AuditEventRecord> result = service.queryAuditEvents(new PageQuery(
            cursor,
            size,
            offset,
            sort,
            extractFilters(params)
        ));
        return ApiResult.ok(new PageResult<>(
            result.items().stream().map(LargeAuditEventRow::from).toList(),
            result.nextCursor(),
            result.totalEstimate(),
            result.totalEstimated(),
            result.hasMore()
        ));
    }

    /**
     * 提交大规模数据批量导出任务端点。
     *
     * @param request 导出资源类型及过滤字段
     * @return 导出任务受理凭证及任务初始状态
     */
    @PostMapping("/exports")
    @PreAuthorize("@perm.has('list.export')")
    public ApiResult<ExportSubmitResponse> submitExportTask(
        @Valid @RequestBody ExportSubmitRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ApiResult.ok(service.submitExportTask(request.withIdempotencyKey(idempotencyKey)));
    }

    /**
     * 轮询查询导出任务处理进度端点。
     *
     * @param id 任务全局唯一ID
     * @return 任务当前运行进度、生成文件及元数据快照
     */
    @GetMapping("/exports/{id}")
    @PreAuthorize("@perm.has('list.export')")
    public ApiResult<LargeListExportJob> getExportJob(@PathVariable("id") String id) {
        return ApiResult.ok(service.getExportJob(id));
    }

    /**
     * 安全物理下载导出的 CSV 批量数据文件端点。
     *
     * @param id       任务全局唯一ID
     * @param response HTTP Servlet 响应对象，输出二进制流
     * @throws IOException 文件流写入网络通道异常
     */
    @GetMapping("/exports/{id}/download")
    @PreAuthorize("@perm.has('list.export')")
    public void downloadFile(@PathVariable("id") String id, HttpServletResponse response) throws IOException {
        InputStream is = service.downloadFile(id);
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"export-" + id + ".csv\"");

        try (is; OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
            os.flush();
        }
    }

    private Map<String, String> extractFilters(Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if ("cursor".equals(key) || "size".equals(key) || "offset".equals(key) || "sort".equals(key)) {
                return;
            }
            if (key.startsWith("filter.")) {
                filters.put(key.substring("filter.".length()), value);
                return;
            }
            filters.put(key, value);
        });
        return filters;
    }
}
