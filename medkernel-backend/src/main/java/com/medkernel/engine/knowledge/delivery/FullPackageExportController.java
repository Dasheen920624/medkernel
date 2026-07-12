package com.medkernel.engine.knowledge.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 完整签名医疗资源包的生成、下载、医院预检和原子激活 API。 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/packages")
@DataScope(requireTenant = true)
public class FullPackageExportController {

    private final FullPackageExportService exports;
    private final FullPackagePreflightService preflights;
    private final FullPackageActivationService activations;

    public FullPackageExportController(
            FullPackageExportService exports,
            FullPackagePreflightService preflights,
            FullPackageActivationService activations) {
        this.exports = exports;
        this.preflights = preflights;
        this.activations = activations;
    }

    @PostMapping
    @PreAuthorize("@perm.has('platform.publish')")
    public ApiResult<FullPackageExportResult> export(
            @Valid @RequestBody ExportRequest request) {
        return ApiResult.ok(exports.export(
            request.deliveryId(), request.platformReleaseIdentity()));
    }

    @GetMapping("/{deliveryId}")
    @PreAuthorize("@perm.has('knowledge.export')")
    public ApiResult<FullPackageExportResult> get(@PathVariable String deliveryId) {
        return ApiResult.ok(exports.get(deliveryId));
    }

    @GetMapping("/{deliveryId}/download")
    @PreAuthorize("@perm.has('knowledge.export')")
    public void download(
            @PathVariable String deliveryId,
            HttpServletResponse response) throws IOException {
        FullPackageExportResult metadata = exports.get(deliveryId);
        String safeId = deliveryId.replaceAll("[^A-Za-z0-9_.-]", "_");
        response.setContentType("application/vnd.medkernel.mkp");
        response.setContentLengthLong(metadata.packageFileSize());
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" + safeId + ".mkp\"");
        try (InputStream input = exports.download(deliveryId);
             OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }

    /** 上传真实 `.mkp` 到医院隔离区，返回不修改运行时的固定根验签预览。 */
    @PostMapping(
        path = "/hospitals/{hospitalId}/preflights",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<FullPackagePreflightPreview> preflight(
            @PathVariable String hospitalId,
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "完整医疗资源包上传文件不能为空");
        }
        try (InputStream source = file.getInputStream()) {
            return ApiResult.ok(preflights.preflight(source, hospitalId));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "完整医疗资源包上传流读取失败", exception);
        }
    }

    /** 重验同一隔离对象，并把已确认预览原子物化为新的机构运行修订。 */
    @PostMapping("/hospitals/{hospitalId}/preflights/{preflightId}:activate")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<FullPackageActivation> activate(
            @PathVariable String hospitalId,
            @PathVariable String preflightId,
            @Valid @RequestBody ActivationRequest request) {
        return ApiResult.ok(activations.activate(new FullPackageActivationCommand(
            hospitalId,
            preflightId,
            request.confirmedPreviewDigest(),
            request.expectedCurrentReleaseId()
        )));
    }

    /** 客户端只选择既有平台版本和稳定交付标识，不得提交正文、信任锚或兼容范围。 */
    public record ExportRequest(
        @NotBlank @Size(max = 128) String deliveryId,
        @NotBlank @Size(max = 128) String platformReleaseIdentity
    ) {
    }

    /** 完整包激活的不可变预览确认与机构版本 CAS 条件。 */
    public record ActivationRequest(
        @NotBlank @Size(max = 128) String confirmedPreviewDigest,
        @Size(max = 128) String expectedCurrentReleaseId
    ) {
    }
}
