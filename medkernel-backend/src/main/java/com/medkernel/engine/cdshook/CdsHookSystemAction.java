package com.medkernel.engine.cdshook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * CDS Hooks 可选 system-action；D3 B0 不自动开嘱，仅保留结构化挂点。
 */
public record CdsHookSystemAction(
    String type,
    String description,
    JsonNode resource
) {
    public CdsHookSystemAction {
        resource = resource == null ? NullNode.getInstance() : resource.deepCopy();
    }
}
