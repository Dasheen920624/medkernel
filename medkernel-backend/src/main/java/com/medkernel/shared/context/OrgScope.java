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
    String specialtyId
) {

    public static OrgScope empty() {
        return new OrgScope(null, null, null, null, null, null, null);
    }

    public static OrgScope tenant(String tenantId) {
        return new OrgScope(tenantId, null, null, null, null, null, null);
    }

    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }
}
