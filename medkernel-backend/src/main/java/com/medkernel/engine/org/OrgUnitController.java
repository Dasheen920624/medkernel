package com.medkernel.engine.org;

import java.util.List;
import java.util.Map;

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
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MedKernel v1.0 GA · 试点准备菜单下的组织单元 API。
 *
 * <p>对应宪法 §2.2 "试点准备 → 客户实施向导 / 租户开通" 菜单的后端能力。
 *
 * <p>访问控制（GA-ENG-BASE-02 范例）：
 * <ul>
 *   <li>类级 {@link DataScope}({@code requireTenant=true})：所有方法都必须带租户上下文，否则切面抛 TENANT_CONTEXT_MISSING</li>
 *   <li>方法级 {@code @PreAuthorize("@perm.has('org.read')")}：业务动作权限按 PermissionCode 控制</li>
 * </ul>
 *
 * <p>客户端永远不允许传 tenantId 参数（防越权伪造），均由 RequestContext 隐式注入。
 */
@RestController
@RequestMapping("/api/v1/tenant/org-units")
@DataScope(requireTenant = true)
public class OrgUnitController {

    private final OrgUnitService service;

    public OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<PageResponse<OrgUnit>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageRequest req = new PageRequest(page, size, sort);
        return ApiResult.ok(service.listByCurrentTenant(req));
    }

    @GetMapping("/{code}")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<OrgUnit> get(@PathVariable String code) {
        return ApiResult.ok(service.getByCurrentTenantAndCode(code));
    }

    @GetMapping("/by-level")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<List<OrgUnit>> byLevel(@RequestParam OrgLevel level) {
        return ApiResult.ok(service.listByCurrentTenantAndLevel(level));
    }

    @GetMapping("/children-map")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<Map<String, List<OrgUnit>>> childrenMap() {
        return ApiResult.ok(service.childrenMapByCurrentTenant());
    }

    /**
     * 在当前租户下原子创建组织单元节点（集团/医院/院区/服务点/科室/专病等）。
     *
     * @param dto 组织单元创建请求负载
     * @return 统一返回格式包，包含已落库的实体
     */
    @PostMapping
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<OrgUnit> create(@Valid @RequestBody OrgUnitCreateDto dto) {
        OrgUnit input = new OrgUnit(
            null,
            dto.parentId(),
            null, // tenantId 由 RequestContext 隐式注入
            null, // orgPath 由服务端根据父级闭包生成
            dto.level(),
            dto.code(),
            dto.name(),
            dto.namePinyin(),
            dto.specialtyId(),
            dto.status(),
            null, null, null, null
        );
        return ApiResult.ok(service.createOrgUnit(input));
    }

    /**
     * 组织单元创建传输对象。
     */
    public record OrgUnitCreateDto(
        String parentId,

        @NotNull(message = "组织级别不能为空")
        OrgLevel level,

        @NotBlank(message = "组织编码不能为空")
        String code,

        @NotBlank(message = "组织名称不能为空")
        String name,

        String namePinyin,
        String specialtyId,
        OrgUnitStatus status
    ) {}
}
