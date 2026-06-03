package com.medkernel.engine.org;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 组织单元业务服务。
 *
 * <p>所有方法在执行前都调用 {@link #requireCurrentTenant()}，从 {@link RequestContext} 取出 tenantId；
 * 未鉴权或 JWT 缺少 {@code tenant_id} claim 的请求会被翻译为 {@link ErrorCode#TENANT_CONTEXT_MISSING} 400 错误，
 * 由 {@code GlobalExceptionHandler} 统一返回 ProblemDetail。
 *
 * <p>避免在业务方法签名上暴露 tenantId 参数（客户端永远不应能伪造）。
 */
@Service
public class OrgUnitService {

    private final OrgUnitRepository repository;
    private final OrgHierarchyRepository hierarchyRepository;

    /**
     * 创建组织单元业务服务。
     *
     * @param repository 组织单元仓库
     * @param hierarchyRepository 组织闭包表仓库
     */
    public OrgUnitService(OrgUnitRepository repository, OrgHierarchyRepository hierarchyRepository) {
        this.repository = repository;
        this.hierarchyRepository = hierarchyRepository;
    }

    /**
     * 分页列出当前租户的组织单元。
     *
     * @param request 分页请求
     * @return 当前租户组织单元分页结果
     */
    public PageResponse<OrgUnit> listByCurrentTenant(PageRequest request) {
        String tenantId = requireCurrentTenant();
        int page = request.safePage();
        int size = request.safeSize();
        int offset = request.offset();

        long total = repository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        List<OrgUnit> rows = repository.pageByTenantId(tenantId, offset, size);
        boolean hasNext = (long) page * size < total;
        return new PageResponse<>(rows, page, size, total, hasNext, false);
    }

    /**
     * 按组织编码读取当前租户的组织单元。
     *
     * @param code 组织编码
     * @return 匹配的组织单元
     */
    public OrgUnit getByCurrentTenantAndCode(String code) {
        String tenantId = requireCurrentTenant();
        return repository.findByTenantIdAndCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("组织单元 code=" + code));
    }

    /**
     * 按组织层级列出当前租户的组织单元。
     *
     * @param level 组织层级
     * @return 同层级组织单元列表
     */
    public List<OrgUnit> listByCurrentTenantAndLevel(OrgLevel level) {
        String tenantId = requireCurrentTenant();
        return repository.findByTenantIdAndLevelOrderByCodeAsc(tenantId, level);
    }

    /**
     * 返回按 parentId → children 的扁平映射，供前端按需展开成树。
     *
     * <p>不在服务端构建嵌套树，避免大组织在序列化阶段递归过深或分页缺失；
     * 客户端按需在抽屉里只渲染当前节点的直接子节点（懒加载）。
     */
    public Map<String, List<OrgUnit>> childrenMapByCurrentTenant() {
        String tenantId = requireCurrentTenant();
        List<OrgUnit> all = repository.findByTenantIdOrderByLevelAscCodeAsc(tenantId);
        Map<String, List<OrgUnit>> map = new HashMap<>();
        // 根节点（parentId == null）放到 key = ROOT
        for (OrgUnit unit : all) {
            String parentKey = unit.parentId() == null ? "ROOT" : unit.parentId();
            map.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(unit);
        }
        return map;
    }

    /**
     * 在当前租户下创建新的组织单元。
     *
     * @param input 组织单元输入数据
     * @return 保存后的组织单元
     */
    @Transactional
    public OrgUnit createOrgUnit(OrgUnit input) {
        String tenantId = requireCurrentTenant();
        validateBasicFields(input);

        // 物理唯一性校验
        if (repository.findByTenantIdAndCode(tenantId, input.code()).isPresent()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "同租户下组织单元 code 物理冲突: " + input.code());
        }

        OrgUnit parent = resolveParent(tenantId, input);
        validateRootAndLevel(tenantId, input, parent);

        java.time.Instant now = java.time.Instant.now();
        String actor = currentActor();
        OrgUnit toSave = new OrgUnit(
            null,
            parent == null ? null : parent.id(),
            tenantId,
            buildPath(parent, input.code()),
            input.level(),
            input.code(),
            input.name(),
            input.namePinyin(),
            input.specialtyId(),
            input.status() == null ? OrgUnitStatus.ACTIVE : input.status(),
            now,
            actor,
            now,
            actor
        );

        OrgUnit saved = repository.save(toSave);
        hierarchyRepository.insertClosureForNewNode(tenantId, saved.id(), saved.parentId());
        return saved;
    }

    /**
     * 查询当前租户下指定组织从根节点到自身的完整路径。
     *
     * @param code 目标组织编码
     * @return 根节点到目标节点的组织列表
     */
    public List<OrgUnit> orgPathByCurrentTenant(String code) {
        String tenantId = requireCurrentTenant();
        OrgUnit unit = repository.findByTenantIdAndCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("组织单元 code=" + code));
        return hierarchyRepository.findAncestorsAndSelf(tenantId, unit.id());
    }

    /**
     * 查询当前租户下指定组织及其全部后代节点。
     *
     * @param code 目标组织编码
     * @return 目标节点及其子树组织列表
     */
    public List<OrgUnit> descendantsByCurrentTenant(String code) {
        String tenantId = requireCurrentTenant();
        OrgUnit unit = repository.findByTenantIdAndCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("组织单元 code=" + code));
        return hierarchyRepository.findDescendantsAndSelf(tenantId, unit.id());
    }

    /**
     * 将当前租户下的一棵组织子树迁移到新的父节点下。
     *
     * <p>迁移会同时更新 {@code parent_id}、整棵子树的 {@code org_path} 以及闭包表；
     * 若目标父节点属于被迁移子树自身，则拒绝写入以避免形成环路。
     *
     * @param unitId 被迁移子树根节点标识
     * @param newParentId 新父组织节点标识
     * @return 迁移后的子树根节点
     */
    @Transactional
    public OrgUnit reparentOrgUnit(String unitId, String newParentId) {
        String tenantId = requireCurrentTenant();
        if (!hasText(unitId) || !hasText(newParentId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织迁移必须指定节点与目标父节点");
        }
        OrgUnit unit = repository.findByTenantIdAndId(tenantId, unitId)
            .orElseThrow(() -> ApiException.notFound("组织单元 id=" + unitId));
        OrgUnit parent = repository.findByTenantIdAndId(tenantId, newParentId)
            .orElseThrow(() -> ApiException.notFound("目标父节点 id=" + newParentId));
        if (unit.id().equals(parent.id()) || hierarchyRepository.isDescendant(tenantId, unit.id(), parent.id())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织父子链不能形成环路");
        }
        if (!unit.level().canHaveParent(parent.level())) {
            throw new ApiException(ErrorCode.ORG_LEVEL_INVALID, "组织父级必须是七层树中的直接上一层");
        }
        if (parent.id().equals(unit.parentId())) {
            return unit;
        }

        List<OrgUnit> subtree = hierarchyRepository.findDescendantsAndSelf(tenantId, unit.id());
        if (subtree.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织闭包表缺少当前节点自引用关系");
        }

        java.time.Instant now = java.time.Instant.now();
        String actor = currentActor();
        String oldPath = requireOrgPath(unit);
        String newPath = buildPath(parent, unit.code());
        List<OrgUnit> moved = new ArrayList<>(subtree.size());
        for (OrgUnit node : subtree) {
            moved.add(new OrgUnit(
                node.id(),
                node.id().equals(unit.id()) ? parent.id() : node.parentId(),
                node.tenantId(),
                replacePathPrefix(oldPath, newPath, node.orgPath()),
                node.level(),
                node.code(),
                node.name(),
                node.namePinyin(),
                node.specialtyId(),
                node.status(),
                node.createdAt(),
                node.createdBy(),
                now,
                actor
            ));
        }

        hierarchyRepository.moveSubtree(tenantId, unit.id(), parent.id());
        List<OrgUnit> saved = repository.saveAll(moved);
        return saved.stream()
            .filter(node -> node.id().equals(unit.id()))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "组织迁移结果缺少根节点"));
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(s -> !s.isBlank())
            .orElse("system");
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private OrgUnit resolveParent(String tenantId, OrgUnit input) {
        if (input.parentId() == null || input.parentId().isBlank()) {
            return null;
        }
        return repository.findByTenantIdAndId(tenantId, input.parentId())
            .orElseThrow(() -> ApiException.notFound("父级组织不存在或不属于当前租户"));
    }

    private void validateBasicFields(OrgUnit input) {
        if (input == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织单元不能为空");
        }
        if (!hasText(input.code())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织编码不能为空");
        }
        if (!hasText(input.name())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织名称不能为空");
        }
    }

    private void validateRootAndLevel(String tenantId, OrgUnit input, OrgUnit parent) {
        if (input.level() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织级别不能为空");
        }
        if (parent == null) {
            if (input.level() != OrgLevel.TENANT) {
                throw new ApiException(ErrorCode.ORG_LEVEL_INVALID, "非租户根组织必须指定直接父级组织");
            }
            if (repository.findByTenantIdAndParentIdIsNull(tenantId).isPresent()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "同一租户只能存在一个组织根节点");
            }
            return;
        }
        if (input.level() == OrgLevel.TENANT) {
            throw new ApiException(ErrorCode.ORG_LEVEL_INVALID, "租户根组织不能挂在其他组织之下");
        }
        if (!input.level().canHaveParent(parent.level())) {
            throw new ApiException(ErrorCode.ORG_LEVEL_INVALID, "组织父级必须是七层树中的直接上一层");
        }
    }

    private String buildPath(OrgUnit parent, String code) {
        String cleanCode = code.trim();
        if (parent == null || parent.orgPath() == null || parent.orgPath().isBlank()) {
            return "/" + cleanCode;
        }
        return parent.orgPath() + "/" + cleanCode;
    }

    private String requireOrgPath(OrgUnit unit) {
        if (!hasText(unit.orgPath())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织路径缺失，不能执行组织迁移");
        }
        return unit.orgPath();
    }

    private String replacePathPrefix(String oldPrefix, String newPrefix, String currentPath) {
        if (!hasText(currentPath)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织子树存在空路径，不能执行组织迁移");
        }
        if (currentPath.equals(oldPrefix)) {
            return newPrefix;
        }
        String childPrefix = oldPrefix + "/";
        if (!currentPath.startsWith(childPrefix)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "组织路径与闭包关系不一致，不能执行组织迁移");
        }
        return newPrefix + currentPath.substring(oldPrefix.length());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
