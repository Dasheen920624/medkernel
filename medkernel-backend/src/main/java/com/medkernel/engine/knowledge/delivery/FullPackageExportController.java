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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台完整签名医疗资源包的生成、回读与真实文件下载 API。 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/packages")
@DataScope(requireTenant = true)
public class FullPackageExportController {

    private final FullPackageExportService exports;

    public FullPackageExportController(FullPackageExportService exports) {
        this.exports = exports;
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

    /** 客户端只选择既有平台版本和稳定交付标识，不得提交正文、信任锚或兼容范围。 */
    public record ExportRequest(
        @NotBlank @Size(max = 128) String deliveryId,
        @NotBlank @Size(max = 128) String platformReleaseIdentity
    ) {
    }
}
