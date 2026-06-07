package com.medkernel.engine.plugin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 插件安全边界 REST 入口（D6 OPT-10）。
 */
@RestController
@RequestMapping("/api/v1/plugins")
@DataScope(requireTenant = true)
public class PluginSecurityController {

    private final PluginSecurityService service;

    public PluginSecurityController(PluginSecurityService service) {
        this.service = service;
    }

    /**
     * 查询当前租户插件。
     *
     * @return 插件列表
     */
    @GetMapping
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<PluginListResponse> list() {
        return ApiResult.ok(service.list());
    }

    /**
     * 注册插件能力声明，默认进入待审状态。
     *
     * @param request 插件声明
     * @return 插件实例
     */
    @PostMapping("/register")
    @PreAuthorize("@perm.has('system.manage')")
    public ResponseEntity<ApiResult<PluginResponse>> register(@Valid @RequestBody PluginRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.register(request)));
    }

    /**
     * 授权插件声明能力。
     *
     * @param pluginId 插件 ID
     * @param request 授权请求
     * @return 授权结果
     */
    @PostMapping("/{pluginId}/grants")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<PluginGrantResponse> grant(@PathVariable String pluginId,
                                                @Valid @RequestBody PluginGrantRequest request) {
        return ApiResult.ok(service.grant(pluginId, request));
    }

    /**
     * 禁用插件。
     *
     * @param pluginId 插件 ID
     * @return 插件实例
     */
    @PostMapping("/{pluginId}:disable")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<PluginResponse> disable(@PathVariable String pluginId) {
        return ApiResult.ok(service.disable(pluginId));
    }
}
