package com.medkernel.engine.sandbox;

/**
 * 沙盘场景可运行状态。未完成临床评审或资产铺底的场景不得进入真实引擎编排。
 */
public enum SandboxScenarioStatus {
    READY,
    CLINICAL_REVIEW_REQUIRED
}
