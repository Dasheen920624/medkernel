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
 * MedKernel v1.0 GA · 平台管理入口的组织单元 API。
 *
 * <p>支撑租户开通、组织目录、任职范围和数据范围配置，是医疗引擎与知识生产按机构隔离运行的基础能力。
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
@RequestMapping("/api/v1/engine/org/org-units")
@DataScope(requireTenant = true)
public class OrgUnitController {

    private final OrgUnitService service;
    private final OrgUserDirectoryService userDirectory;

    public OrgUnitController(OrgUnitService service, OrgUserDirectoryService userDirectory) {
        this.service = service;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<PageResponse<OrgUnit>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrgLevel level,
            @RequestParam(required = false) OrgUnitStatus status,
            @RequestParam(required = false) OrgDirectoryScope scope,
            @RequestParam(required = false) String ancestorId) {
        PageRequest req = new PageRequest(page, size, sort);
        if (keyword != null || level != null || status != null || scope != null || ancestorId != null) {
            return ApiResult.ok(
                service.searchByCurrentTenant(req, keyword, level, status, scope, ancestorId));
        }
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
     * 分页读取当前租户的启用用户，供责任人等业务字段选择。
     */
    @GetMapping("/users")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<PageResponse<OrgUserDirectoryItem>> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        if (keyword != null) {
            return ApiResult.ok(userDirectory.search(
                new PageRequest(page, size, "displayName,asc"), keyword));
        }
        return ApiResult.ok(userDirectory.list(new PageRequest(page, size, "displayName,asc")));
    }

    /**
     * 在当前租户下原子创建组织单元节点（集团/医院/院区/服务点/科室等）。
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
            dto.facilityType(),
            dto.specialtyId(),
            dto.status(),
            null, null, null, null
        );
        return ApiResult.ok(service.createOrgUnit(input));
    }

    @GetMapping("/{code}/resolution-path")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<List<OrgUnit>> resolutionPath(@PathVariable String code) {
        return ApiResult.ok(service.resolutionPathByCurrentTenant(code));
    }

    @PostMapping("/{id}/secondary-parents")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<Void> addSecondaryParent(
            @PathVariable String id,
            @Valid @RequestBody OrgSecondaryParentDto dto) {
        service.addSecondaryParent(id, dto.secondaryParentId(), dto.relationCode(), dto.priority() == null ? 0 : dto.priority());
        return ApiResult.ok(null);
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
        OrgFacilityType facilityType,
        String specialtyId,
        OrgUnitStatus status
    ) {}

    /**
     * 组织次级归属创建传输对象。
     */
    public record OrgSecondaryParentDto(
        @NotBlank(message = "次级父节点不能为空")
        String secondaryParentId,
        String relationCode,
        Integer priority
    ) {}
}
