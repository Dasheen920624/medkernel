package com.medkernel.engine.recommendation;

/**
 * 推荐评估的模型能力状态。
 *
 * <p>当前 API-07 只承诺无模型确定性基线；未接入真实模型网关时必须返回
 * {@link #MODEL_DISABLED}，并过滤 AI 生成候选卡。
 */
public enum RecommendationModelStatus {
    MODEL_DISABLED
}
