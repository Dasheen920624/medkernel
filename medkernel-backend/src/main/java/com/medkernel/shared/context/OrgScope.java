package com.medkernel.shared.context;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 组织上下文快照，对应核心 §9 所需的组织树与专病横切维度。
 *
 * <p>所有 API、规则执行、路径判断、推荐生成、质控评估都必须携带这些字段，
 * 由 GA-ENG-BASE-01 / GA-ENG-BASE-02 在 JWT 解析阶段填充并注入 {@link RequestContext}。
 *
 * @param tenantId     租户 ID（必填）
 * @param groupId      集团 ID
 * @param hospitalId   医院 ID
 * @param campusId     院区 / 分院 ID
 * @param siteId       社区卫生服务中心 / 医联体成员 ID
 * @param departmentId 科室 ID
 * @param wardId       病区 ID
 * @param specialtyId  专病横切适用维度 ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgScope(
    String tenantId,
    String groupId,
    String hospitalId,
    String campusId,
    String siteId,
    String departmentId,
    String wardId,
    String specialtyId
) {

    /**
     * 兼容不携带病区维度的既有调用；新代码应优先使用完整构造器。
     */
    public OrgScope(
            String tenantId,
            String groupId,
            String hospitalId,
            String campusId,
            String siteId,
            String departmentId,
            String specialtyId) {
        this(
            tenantId,
            groupId,
            hospitalId,
            campusId,
            siteId,
            departmentId,
            null,
            specialtyId);
    }

    public static OrgScope empty() {
        return new OrgScope(null, null, null, null, null, null, null, null);
    }

    public static OrgScope tenant(String tenantId) {
        return new OrgScope(tenantId, null, null, null, null, null, null, null);
    }

    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }

    /**
     * 返回组织树中距离当前上下文最近的真实组织节点；专病是横切维度，不属于组织树。
     */
    public String nearestOrgUnitId() {
        if (hasText(wardId)) {
            return wardId;
        }
        if (hasText(departmentId)) {
            return departmentId;
        }
        if (hasText(siteId)) {
            return siteId;
        }
        if (hasText(campusId)) {
            return campusId;
        }
        if (hasText(hospitalId)) {
            return hospitalId;
        }
        if (hasText(groupId)) {
            return groupId;
        }
        return null;
    }

    /**
     * 返回最近组织节点；无组织节点时回退到租户，用于审计和快照归属。
     */
    public String nearestOrgUnitIdOrTenant(String fallbackTenantId) {
        String orgUnitId = nearestOrgUnitId();
        if (hasText(orgUnitId)) {
            return orgUnitId;
        }
        if (hasText(tenantId)) {
            return tenantId;
        }
        return fallbackTenantId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
