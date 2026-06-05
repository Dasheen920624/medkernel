package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 推荐卡类型枚举：诊断辅助卡（运行时鉴别诊断 Plan B）类型存在。 */
class RecommendationCardTypeTest {

    @Test
    void diagnosisTypeExists() {
        assertThat(RecommendationCardType.valueOf("DIAGNOSIS")).isNotNull();
    }
}
