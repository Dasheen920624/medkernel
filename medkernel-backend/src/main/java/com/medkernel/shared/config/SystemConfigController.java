package com.medkernel.shared.config;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

import jakarta.validation.Valid;

/**
 * 系统配置中心控制器（CONFIG-01）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/system/configs")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<List<SystemConfigItemResponse>> list(@RequestParam(required = false) String prefix) {
        return ApiResult.ok(service.list(prefix));
    }

    @PatchMapping("/{key:.+}")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<SystemConfigItemResponse> update(@PathVariable String key,
                                                      @Valid @RequestBody SystemConfigUpdateRequest request,
                                                      Authentication authentication) {
        String actor = SystemConfigService.currentActor(authentication == null ? null : authentication.getName());
        return ApiResult.ok(service.update(key, request, actor));
    }
}
