package com.medkernel.engine.knowledge.production;

/**
 * 知识候选生产器（AIK-STD-13，FR-2 四生产器可插拔）。对应 {@code mk_knowledge_production_job.producer}。
 *
 * <p>四生产器产物统一进候选池走同一流水线；新增生产器不破坏框架。{@link #MANUAL} 与确定性产物（如 LLM-06
 * 受控来源探索）为 B0 路径，无模型即可跑；外部模型生产器经 LLM-01/08 网关路由，真实调用受 P6 闸控。
 */
public enum KnowledgeProducer {
    /** ① API 大模型自主（B2 外部，经模型网关）。 */
    API_MODEL,
    /** ② Agent 工具协助（经 DATASVC-01 MCP/CLI 回写）。 */
    AGENT_TOOL,
    /** ③ 本地大模型（B1 本地 / 国产化，不出网）。 */
    LOCAL_MODEL,
    /** ④ 人工录入 / 批量导入（B0，关模型仍可跑）。 */
    MANUAL
}
