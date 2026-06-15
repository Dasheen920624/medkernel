package com.medkernel.engine.knowledge.discovery;

import jakarta.validation.constraints.NotBlank;

/**
 * 可信来源探索请求（LLM-06）。
 *
 * @param query 探索查询词（受控来源关键词），非空
 * @param limit 受控命中上限（可空，服务端默认 20 / 上限 50）
 */
public record DiscoveryRequest(
    @NotBlank String query,
    Integer limit
) {
}
