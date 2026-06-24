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
    private final DomainFacadeB0FixtureService fixtureService;

    public DomainFacadeController(DomainFacadeCatalogService service, DomainFacadeB0FixtureService fixtureService) {
        this.service = service;
        this.fixtureService = fixtureService;
    }

    /** 列举 X-DOMAIN 17 张领域门面和业务服务组合目录。 */
    @GetMapping
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DomainFacadeDefinition>> list() {
        return ApiResult.ok(service.listDefinitions());
    }

    /** 列举 X-DOMAIN 17 张领域门面的 B0 主链路 fixture 证据。 */
    @GetMapping("/b0-fixtures")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DomainFacadeB0FixtureEvidence>> listB0Fixtures() {
        return ApiResult.ok(fixtureService.listFixtureEvidence());
    }

    /** 查询单个领域门面或业务组合的复用链路。 */
    @GetMapping("/{code}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DomainFacadeDefinition> get(@PathVariable String code) {
        return ApiResult.ok(service.requireDefinition(code));
    }

    /** 查询单个领域门面或业务组合的 B0 主链路 fixture 证据。 */
    @GetMapping("/{code}/b0-fixture")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DomainFacadeB0FixtureEvidence> getB0Fixture(@PathVariable String code) {
        return ApiResult.ok(fixtureService.requireFixtureEvidence(code));
    }
}
