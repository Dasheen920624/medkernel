package com.medkernel.engine.cdshook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * CDS Hooks 卡片建议项。
 *
 * <p>建议项只携带可审阅的结构化载荷，不等同于自动执行动作。
 */
public record CdsHookSuggestion(
    String label,
    String actionType,
    JsonNode payload
) {
    public CdsHookSuggestion {
        payload = payload == null ? NullNode.getInstance() : payload.deepCopy();
    }
}
