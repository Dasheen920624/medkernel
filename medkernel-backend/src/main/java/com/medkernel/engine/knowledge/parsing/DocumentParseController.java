package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medkernel.engine.knowledge.material.DocumentMaterialResponse;
import com.medkernel.engine.knowledge.material.DocumentMaterialService;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.datascope.DataScope;

/**
 * 文档解析 API（AIK-STD-02）。解析 = 把受控来源文档解析为带锚点的来源片段（走 {@code knowledge.write}，
 * 等同新增受控来源内容）；解析 job 台账供查询（走 {@code knowledge.read}）。
 * 类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class DocumentParseController {

    private final DocumentParseOrchestrationService service;
    private final DocumentMaterialService materials;
    private final ObjectMapper objectMapper;

    public DocumentParseController(DocumentParseOrchestrationService service,
                                   DocumentMaterialService materials,
                                   ObjectMapper objectMapper) {
        this.service = service;
        this.materials = materials;
        this.objectMapper = objectMapper;
    }

    /** 提交一次文档解析（解析为带锚点片段并物化进受控来源）。 */
    @PostMapping("/documents:parse")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocParseJob> parse(@Valid @RequestBody DocumentParseRequest request) {
        return ApiResult.ok(service.submit(request));
    }

    /** 院内上传解析：multipart 原件进入同一受管资料库，候选生成固定归院内覆盖管道。 */
    @PostMapping(path = "/documents:upload-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocumentParseResponse> uploadParse(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long sourceDocumentId,
            @RequestParam String versionNo,
            @RequestParam DocumentFormat format,
            @RequestPart(value = "generation", required = false) String generation) {
        return ApiResult.ok(service.submitTenantUpload(
            uploadRequest(file, sourceDocumentId, versionNo, format),
            uploadGeneration(generation)));
    }

    /** 从受管资料库取回原件并创建一次新的重解析 job。 */
    @PostMapping("/documents/parse-jobs/{jobCode}:reparse")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocParseJob> reparse(@PathVariable String jobCode) {
        return ApiResult.ok(service.reparse(jobCode));
    }

    /** 查询单个解析 job 状态。 */
    @GetMapping("/documents/parse-jobs/{jobCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DocParseJob> getJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.getJob(jobCode));
    }

    /** 分页查询解析 job 台账。 */
    @GetMapping("/documents/parse-jobs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<DocParseJob>> listJobs(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listJobs(page, size));
    }

    /** 读取已入库文档原件，返回 Base64 内容并记录审计。 */
    @GetMapping("/materials/{materialId}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DocumentMaterialResponse> getMaterial(@PathVariable Long materialId) {
        return ApiResult.ok(materials.getMaterial(materialId));
    }

    private DocumentParseRequest uploadRequest(MultipartFile file, Long sourceDocumentId, String versionNo,
                                               DocumentFormat format) {
        if (sourceDocumentId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "sourceDocumentId 不能为空");
        }
        if (versionNo == null || versionNo.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "versionNo 不能为空");
        }
        if (format == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "format 不能为空");
        }
        byte[] bytes = uploadBytes(file);
        return new DocumentParseRequest(
            sourceDocumentId,
            versionNo,
            uploadFileName(file, format),
            format,
            uploadContent(bytes, format));
    }

    private DocumentUploadGenerationRequest uploadGeneration(String generation) {
        if (generation == null || generation.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(generation, DocumentUploadGenerationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内上传候选生成计划须为合法 JSON", exception);
        }
    }

    private static byte[] uploadBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "上传文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "上传文件读取失败", exception);
        }
    }

    private static String uploadContent(byte[] bytes, DocumentFormat format) {
        return format == DocumentFormat.STRUCTURED_TEXT
            ? new String(bytes, UTF_8)
            : Base64.getEncoder().encodeToString(bytes);
    }

    private static String uploadFileName(MultipartFile file, DocumentFormat format) {
        String original = file == null ? null : file.getOriginalFilename();
        if (original != null && !original.isBlank()) {
            return original;
        }
        return "tenant-upload" + switch (format) {
            case STRUCTURED_TEXT -> ".txt";
            case PDF -> ".pdf";
            case WORD -> ".docx";
        };
    }
}
