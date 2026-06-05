package com.medkernel.engine.evaluation;

/**
 * 评估质控模型状态。
 *
 * <p>API-08 当前只交付确定性 B0 主链路，未接入模型增强时必须显式返回 {@link #MODEL_DISABLED}。
 */
public enum EvaluationModelStatus {
    MODEL_DISABLED
}
