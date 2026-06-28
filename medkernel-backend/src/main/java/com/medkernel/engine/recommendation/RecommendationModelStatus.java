package com.medkernel.engine.recommendation;

/**
 * 推荐评估的模型能力状态。
 *
 * <p>推荐链路以无模型确定性基线为主链；没有可用模型服务时必须返回
 * {@link #MODEL_DISABLED}，并过滤 AI 生成候选卡。
 */
public enum RecommendationModelStatus {
    MODEL_DISABLED
}
