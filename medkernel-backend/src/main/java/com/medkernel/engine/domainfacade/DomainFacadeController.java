package com.medkernel.engine.domainfacade;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * X-DOMAIN 领域门面组合目录控制器。
 *
 * <p>只读暴露领域门面复用链路，供生产中心和验收工具确认 17 张门面卡已进入同一引擎链路。
 */
@RestController
@RequestMapping("/api/v1/engine/domain-facades")
@DataScope(requireTenant = true)
public class DomainFacadeController {

    private final DomainFacadeCatalogService service;

    public DomainFacadeController(DomainFacadeCatalogService service) {
        this.service = service;
    }

    /** 列举 X-DOMAIN 17 张领域门面和服务包组合目录。 */
    @GetMapping
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DomainFacadeDefinition>> list() {
        return ApiResult.ok(service.listDefinitions());
    }

    /** 查询单个领域门面或服务包的复用链路。 */
    @GetMapping("/{code}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DomainFacadeDefinition> get(@PathVariable String code) {
        return ApiResult.ok(service.requireDefinition(code));
    }
}
