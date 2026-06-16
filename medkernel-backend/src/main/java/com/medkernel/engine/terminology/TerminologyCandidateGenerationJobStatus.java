package com.medkernel.engine.terminology;

/**
 * 术语候选生成异步任务状态机。
 */
public enum TerminologyCandidateGenerationJobStatus {
    /** 已提交，等待后台执行 */
    PENDING,
    /** 后台正在生成候选 */
    RUNNING,
    /** 生成完成，可通过 candidatePageUri 分页查看候选 */
    SUCCEEDED,
    /** 生成失败，errorMessage 含真实原因 */
    FAILED,
    /** 用户取消或后续调度取消 */
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
