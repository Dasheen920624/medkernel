package com.medkernel.engine.evaluation;

/**
 * 评估质控模型状态。
 *
 * <p>评价链路以确定性 B0 主链路为基础；没有可用模型赋能时必须显式返回 {@link #MODEL_DISABLED}。
 */
public enum EvaluationModelStatus {
    MODEL_DISABLED
}
