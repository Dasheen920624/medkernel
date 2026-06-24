package com.medkernel.engine.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * MedKernel v1.0 GA · 知识异步导出 API（GA-ENG-API-03）。
 *
 * <p>大规模列表（详细规范 §1.5 / §10.2）不允许同步导出 — 客户端先 POST 创建作业，
 * 然后轮询 GET 状态，最后由 result_uri 拉取结果文件。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/exports")
@DataScope(requireTenant = true)
public class KnowledgeExportController {

    private final KnowledgeExportService exportService;

    public KnowledgeExportController(KnowledgeExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    @PreAuthorize("@perm.has('knowledge.export')")
    public ApiResult<KnowledgeExportJob> submit(@Valid @RequestBody SubmitExportRequest req) {
        req.context().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(exportService.submit(req.type(), req.filterJson()));
    }

    @GetMapping("/{jobCode}")
    @PreAuthorize("@perm.has('knowledge.export')")
    public ApiResult<KnowledgeExportJob> get(@PathVariable String jobCode) {
        return ApiResult.ok(exportService.get(jobCode));
    }

    @GetMapping
    @PreAuthorize("@perm.has('knowledge.export')")
    public ApiResult<PageResponse<KnowledgeExportJob>> listRecent(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(exportService.listRecent(new PageRequest(page, size, null)));
    }

    @PostMapping("/{jobCode}/cancel")
    @PreAuthorize("@perm.has('knowledge.export')")
    public ApiResult<KnowledgeExportJob> cancel(@PathVariable String jobCode) {
        return ApiResult.ok(exportService.cancel(jobCode));
    }

    @GetMapping("/{jobCode}/download")
    @PreAuthorize("@perm.has('knowledge.export')")
    public void download(@PathVariable String jobCode, HttpServletResponse response) throws IOException {
        InputStream input = exportService.downloadFile(jobCode);
        String safeJobCode = jobCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/x-ndjson;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"knowledge-export-" + safeJobCode + ".jsonl\"");
        try (input; OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    /** 提交导出作业请求体。filterJson 是可选的 JSON 字符串，按 type 不同语义不同。 */
    public record SubmitExportRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("group_id") String groupId,
        @JsonProperty("hospital_id") String hospitalId,
        @JsonProperty("campus_id") String campusId,
        @JsonProperty("site_id") String siteId,
        @JsonProperty("department_id") String departmentId,
        @JsonProperty("specialty_id") String specialtyId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("role_codes") List<String> roleCodes,
        @NotNull ExportType type,
        @Size(max = 2000) String filterJson
    ) {

        public SubmitExportRequest {
            roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        }

        KnowledgeApiContext context() {
            return KnowledgeApiContext.from(
                requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
                departmentId, specialtyId, userId, roleCodes
            );
        }
    }
}
