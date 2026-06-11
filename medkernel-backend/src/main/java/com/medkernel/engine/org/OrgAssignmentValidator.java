package com.medkernel.engine.org;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.RequestContext;

/**
 * 统一校验业务责任科室与责任人，防止跨租户、停用或错误层级对象进入任务事实。
 */
@Service
public class OrgAssignmentValidator {

    private final OrgUnitRepository orgUnits;
    private final TenantUserRepository users;

    public OrgAssignmentValidator(OrgUnitRepository orgUnits, TenantUserRepository users) {
        this.orgUnits = orgUnits;
        this.users = users;
    }

    public void requireActiveDepartment(String departmentId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        OrgUnit department = orgUnits.findByTenantIdAndId(tenantId, departmentId)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "责任科室不存在"));
        if (department.level() != OrgLevel.DEPARTMENT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "责任组织必须是科室");
        }
        if (!department.isActive()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "责任科室未启用");
        }
    }

    public void requireActiveUserIfPresent(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String tenantId = RequestContext.currentOrgScope().tenantId();
        var user = users.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "责任人不存在"));
        if (!user.active()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "责任人未启用");
        }
    }

    /**
     * 校验数据权限引用的组织节点均属于指定租户、层级正确、处于启用状态且层级链一致。
     */
    public void requireActiveScopeReferences(
            String tenantId,
            String groupId,
            String hospitalId,
            String campusId,
            String siteId,
            String departmentId,
            String wardId,
            String specialtyId) {
        String facilityId = StringUtils.hasText(hospitalId) ? hospitalId : siteId;
        List<ScopeReference> references = List.of(
            new ScopeReference(groupId, OrgLevel.REGION, "区域"),
            new ScopeReference(facilityId, OrgLevel.FACILITY, "机构"),
            new ScopeReference(campusId, OrgLevel.CAMPUS, "院区"),
            new ScopeReference(departmentId, OrgLevel.DEPARTMENT, "科室"),
            new ScopeReference(wardId, OrgLevel.WARD, "病区")
        );
        OrgUnit ancestor = null;
        String ancestorLabel = null;
        OrgUnit selectedDepartment = null;
        for (ScopeReference reference : references) {
            if (!StringUtils.hasText(reference.id())) {
                continue;
            }
            OrgUnit current = requireActiveOrgUnit(
                tenantId,
                reference.id().trim(),
                reference.level(),
                reference.label());
            if (ancestor != null && !isDescendantOf(tenantId, current, ancestor.id())) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    reference.label() + "不属于已选" + ancestorLabel);
            }
            ancestor = current;
            ancestorLabel = reference.label();
            if (reference.level() == OrgLevel.DEPARTMENT) {
                selectedDepartment = current;
            }
        }
        requireActiveSpecialty(tenantId, specialtyId, selectedDepartment);
    }

    public void requireActiveScopeReferences(
            String tenantId,
            String groupId,
            String hospitalId,
            String campusId,
            String siteId,
            String departmentId,
            String specialtyId) {
        requireActiveScopeReferences(
            tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, null, specialtyId);
    }

    private OrgUnit requireActiveOrgUnit(
            String tenantId,
            String orgUnitId,
            OrgLevel expectedLevel,
            String label) {
        OrgUnit unit = orgUnits.findByTenantIdAndId(tenantId, orgUnitId)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, label + "不存在"));
        if (unit.level() != expectedLevel) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "组织层级不正确");
        }
        if (!unit.isActive()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "未启用");
        }
        return unit;
    }

    private boolean isDescendantOf(String tenantId, OrgUnit child, String ancestorId) {
        String parentId = child.parentId();
        Set<String> visited = new HashSet<>();
        while (StringUtils.hasText(parentId) && visited.add(parentId)) {
            if (ancestorId.equals(parentId)) {
                return true;
            }
            parentId = orgUnits.findByTenantIdAndId(tenantId, parentId)
                .map(OrgUnit::parentId)
                .orElse(null);
        }
        return false;
    }

    private void requireActiveSpecialty(
            String tenantId,
            String specialtyId,
            OrgUnit selectedDepartment) {
        if (!StringUtils.hasText(specialtyId)) {
            return;
        }
        String normalized = specialtyId.trim();
        boolean active = orgUnits.findByTenantIdAndSpecialtyIdOrderByCodeAsc(tenantId, normalized)
            .stream()
            .anyMatch(OrgUnit::isActive);
        if (!active) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "专科不存在或未启用");
        }
        if (selectedDepartment != null
                && StringUtils.hasText(selectedDepartment.specialtyId())
                && !normalized.equals(selectedDepartment.specialtyId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "专科与已选科室不一致");
        }
    }

    private record ScopeReference(String id, OrgLevel level, String label) {
    }
}
