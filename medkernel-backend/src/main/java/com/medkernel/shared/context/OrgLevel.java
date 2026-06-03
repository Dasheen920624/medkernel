package com.medkernel.shared.context;

/**
 * 组织层级枚举。对应核心 §9 的七层组织树。
 *
 * @see OrgScope
 */
public enum OrgLevel {
    /** 平台租户根 */
    TENANT,
    /** 集团 */
    GROUP,
    /** 医院 */
    HOSPITAL,
    /** 院区 / 分院 */
    CAMPUS,
    /** 社区卫生服务中心 / 街道卫生所 / 医联体成员机构 */
    SITE,
    /** 科室 */
    DEPARTMENT,
    /** 专病 / 专科维度 */
    SPECIALTY;

    /**
     * 判断当前层级是否允许挂在指定直接父层级之下。
     *
     * @param parent 候选父组织层级
     * @return 父层级是七层树中的直接上一层时返回 {@code true}
     */
    public boolean canHaveParent(OrgLevel parent) {
        return parent != null && parent.ordinal() + 1 == ordinal();
    }
}
