package com.medkernel.shared.runtime;

import java.nio.charset.StandardCharsets;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * 系统运维快照控制器（BASE-07）。
 *
 * <p>提供当前系统的应用名称、运行环境、部署模式、国产化适配自检概貌、依赖连接及备份就绪度探测等快照数据服务。
 * 全线受动作级权限鉴权保护，确保敏感内网运维拓扑安全不外泄。
 */
@RestController
@RequestMapping("/api/v1/system")
public class RuntimeOperationsController {

    private final RuntimeOperationsService service;

    /**
     * 构造函数。
     *
     * @param service 系统运维快照业务服务
     */
    public RuntimeOperationsController(RuntimeOperationsService service) {
        this.service = service;
    }

    /**
     * 扫描获取当前系统全量运维状态与国产化适配自检快照。
     *
     * @return 全量运维状态及自检快照信息
     */
    @GetMapping("/operations")
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<RuntimeOperationsSnapshot> operations() {
        return ApiResult.ok(service.snapshot());
    }

    /**
     * 导出国产化适配自检报告。
     *
     * @return 文本格式国产化适配自检报告
     */
    @GetMapping(value = "/operations/domestic-report", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("@perm.has('system.read')")
    public ResponseEntity<String> domesticReport() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=medkernel-domestic-check.txt")
            .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
            .body(service.domesticReport());
    }
}
