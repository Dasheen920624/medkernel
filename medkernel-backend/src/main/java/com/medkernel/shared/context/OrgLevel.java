package com.medkernel.shared.context;

/**
 * 组织层级枚举。
 *
 * <p>声明顺序即层级高低（根在前、叶在后）：PLATFORM 平台权威层高于所有租户，其后是
 * 租户组织树 TENANT→REGION→FACILITY→CAMPUS→DEPARTMENT→WARD。专病通过
 * applicableScope / specialtyId 表达为横切维度，不作为组织树节点。
 * {@link #canHaveParent(OrgLevel)} 据此判定父子层级关系。
 *
 * @see OrgScope
 */
public enum OrgLevel {
    /** 平台权威层：高于所有租户的只读权威源，与租户分离。 */
    PLATFORM,
    /** 租户根（法人 / 集团账户）。 */
    TENANT,
    /** 联合体 / 区域：集团、医联体、医共体或区域卫健。 */
    REGION,
    /** 医疗机构：医院、社区卫生服务中心、卫生院或站点，具体类型见 facilityType。 */
    FACILITY,
    /** 院区 / 分院 */
    CAMPUS,
    /** 科室 */
    DEPARTMENT,
    /** 病区 / 护理单元。 */
    WARD;

    /**
     * 判断当前层级是否允许挂在指定父层级之下。
     *
     * <p>放宽为「父层级严格高于子层级即可」，允许跳过中间的可选层（如科室直挂医院），
     * 不再强制仅紧邻上一层；父层级与子层级同级或更低时不允许。
     *
     * @param parent 候选父组织层级
     * @return 父层级严格高于当前层级时返回 {@code true}
     */
    public boolean canHaveParent(OrgLevel parent) {
        return parent != null && parent.ordinal() < ordinal();
    }

    /**
     * 是否可作为新建组织树节点。
     *
     * <p>PLATFORM 是权威层，不属于租户组织树。
     *
     * @return 可作为租户组织树节点时返回 {@code true}
     */
    public boolean isOrganizationTreeLevel() {
        return this != PLATFORM;
    }
}
