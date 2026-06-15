package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;

/**
 * 物化目标声明单元测试（AIK-STD-13 PR4，二选一校验）。
 */
class MaterializationTargetTest {

    @Test
    void existingIdentityTargetIsValid() {
        MaterializationTarget t = new MaterializationTarget(5L, null);
        t.validate();
        assertThat(t.targetIdentityId()).isEqualTo(5L);
    }

    @Test
    void newIdentityTargetIsValid() {
        MaterializationTarget t = new MaterializationTarget(null,
            new NewIdentitySpec(KnowledgeDomain.GUIDELINE, "二甲双胍说明书", "KN-METFORMIN"));
        t.validate();
        assertThat(t.newIdentity().subject()).isEqualTo("二甲双胍说明书");
    }

    @Test
    void bothSetIsRejected() {
        MaterializationTarget t = new MaterializationTarget(5L,
            new NewIdentitySpec(KnowledgeDomain.GUIDELINE, "s", "KN-1"));
        assertThatThrownBy(t::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neitherSetIsRejected() {
        assertThatThrownBy(() -> new MaterializationTarget(null, null).validate())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
