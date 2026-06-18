package com.medkernel.engine.knowledge.discovery;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 可信来源探索结果（LLM-06，FR-3/4/5）。
 *
 * <p>{@code candidates} 为经 AIK-STD-01 校验闸通过的 DRAFT 候选信封（每条带真实来源锚点 + 可信分级），
 * 交 AIK-STD-13 落候选池/审核链，<b>不写权威库</b>。无受控匹配时 {@code status=EMPTY} 且候选为空（诚实空态）。
 *
 * @param runCode 运行业务键
 * @param executedAt 检索时点
 * @param status 运行状态
 * @param degraded 是否诚实降级
 * @param hitCount 命中受控片段数
 * @param candidateCount 产出候选数
 * @param resultHash 结果集真实指纹（可复算核验）
 * @param candidates 候选信封（DRAFT）
 * @param diffs 与目标现行知识身份的差异检测结果；未绑定目标身份时为空列表
 */
public record DiscoveryResponse(
    String runCode,
    Instant executedAt,
    DiscoveryRunStatus status,
    boolean degraded,
    int hitCount,
    int candidateCount,
    String resultHash,
    List<KnowledgeAssetEnvelope> candidates,
    List<KnowledgeDiffDetection> diffs
) {
    public DiscoveryResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        diffs = diffs == null ? List.of() : List.copyOf(diffs);
    }
}
