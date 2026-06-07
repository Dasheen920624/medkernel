package com.medkernel.shared.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 组织层级父子规则单元测试：覆盖放宽后的「父层级严格高于子级即可（可跳级）」与新增 PLATFORM 平台层。
 */
class OrgLevelTest {

    @Test
    void platformIsTheHighestAuthorityTier() {
        // 平台层高于租户：任意租户树层级都可挂在平台层之下
        assertThat(OrgLevel.TENANT.canHaveParent(OrgLevel.PLATFORM)).isTrue();
        assertThat(OrgLevel.GROUP.canHaveParent(OrgLevel.PLATFORM)).isTrue();
        // 平台层是顶层，本身不能再有父级
        assertThat(OrgLevel.PLATFORM.canHaveParent(OrgLevel.TENANT)).isFalse();
        assertThat(OrgLevel.PLATFORM.canHaveParent(null)).isFalse();
    }

    @Test
    void allowsAdjacentAndSkippedHigherParent() {
        // 紧邻上一层仍然允许
        assertThat(OrgLevel.CAMPUS.canHaveParent(OrgLevel.HOSPITAL)).isTrue();
        // 跳过可选层允许：科室直挂医院
        assertThat(OrgLevel.DEPARTMENT.canHaveParent(OrgLevel.HOSPITAL)).isTrue();
    }

    @Test
    void organizationTreeLevelsExcludeSpecialtyDimension() {
        assertThat(OrgLevel.values())
            .containsExactly(
                OrgLevel.PLATFORM,
                OrgLevel.TENANT,
                OrgLevel.GROUP,
                OrgLevel.HOSPITAL,
                OrgLevel.CAMPUS,
                OrgLevel.SITE,
                OrgLevel.DEPARTMENT
            );
        assertThat(OrgLevel.PLATFORM.isOrganizationTreeLevel()).isFalse();
        assertThat(OrgLevel.DEPARTMENT.isOrganizationTreeLevel()).isTrue();
    }

    @Test
    void rejectsSameOrLowerParentLevel() {
        // 同级不允许
        assertThat(OrgLevel.DEPARTMENT.canHaveParent(OrgLevel.DEPARTMENT)).isFalse();
        // 父级层级更低（倒挂）不允许
        assertThat(OrgLevel.GROUP.canHaveParent(OrgLevel.DEPARTMENT)).isFalse();
        assertThat(OrgLevel.HOSPITAL.canHaveParent(OrgLevel.CAMPUS)).isFalse();
        // 空父级不允许
        assertThat(OrgLevel.DEPARTMENT.canHaveParent(null)).isFalse();
    }
}
