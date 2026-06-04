package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 诊断知识资产域枚举测试（Plan A Task 1：新增 DIAGNOSIS 域）。
 */
class KnowledgeDomainTest {

    @Test
    void diagnosisDomainExists() {
        assertThat(KnowledgeDomain.valueOf("DIAGNOSIS")).isNotNull();
    }
}
