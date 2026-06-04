package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** 红线端口默认实现：未接线时返回空、不抛错（诚实降级，不阻断主链路）。 */
class DefaultDiagnosisRedlinePortTest {

    @Test
    void noRedlineRulesYieldsEmptyNotError() {
        var port = new DefaultDiagnosisRedlinePort();
        assertThat(port.check("t-1", Set.of("STD-FEVER"))).isEmpty();
    }
}
