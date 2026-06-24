package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PathwayProgressorFeatureGateTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void richNodeDisabledFailsHonestlyBeforeRuntimeProgression() {
        AuthoringFeatureGate gate = mock(AuthoringFeatureGate.class);
        when(gate.enabled(AuthoringFeatureFlag.PATHWAY_RICH_NODES)).thenReturn(false);
        PathwayProgressor progressor = new PathwayProgressor(json, new ConditionEvaluator(json), gate);
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        PathwayNode orderSet = new PathwayNode(
            1L,
            "node-order-set",
            "tenant-A",
            "tpl-1",
            "N1",
            "医嘱套餐",
            PathwayNodeType.ORDER_SET,
            null,
            1,
            "clinical-user",
            "clinical-user",
            "[]",
            "[]",
            null,
            null,
            false,
            "{\"orderSetRef\":\"ORDER_SET.CKD\"}",
            now,
            "tester",
            now,
            "tester",
            "trace-1");
        PathwayNode next = new PathwayNode(
            2L,
            "node-next",
            "tenant-A",
            "tpl-1",
            "N2",
            "复核",
            PathwayNodeType.ASSESSMENT,
            null,
            2,
            "clinical-user",
            "clinical-user",
            "[]",
            "[]",
            null,
            null,
            true,
            "{}",
            now,
            "tester",
            now,
            "tester",
            "trace-1");
        PathwayEdge edge = new PathwayEdge(
            1L,
            "edge-1",
            "tenant-A",
            "tpl-1",
            "E1",
            "N1",
            "N2",
            PathwayEdgeType.DEFAULT,
            null,
            1,
            now,
            "tester",
            now,
            "tester",
            "trace-1");

        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            new PathwayGraph(List.of(orderSet, next), List.of(edge)),
            "N1",
            PathwayAdvanceEventType.COMPLETE,
            null)))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.ENG_PATHWAY_006))
            .hasMessageContaining("路径富节点能力开关未启用")
            .hasMessageContaining("authoring-pathway-rich-nodes");
    }
}
