package com.medkernel.engine.knowledge.parsing;

/** 文档解析 job 状态（AIK-STD-02）。对应 mk_doc_parse_job.status CHECK 约束。 */
public enum ParseJobStatus {
    /** 已建任务待解析。 */
    PENDING,
    /** 解析进行中。 */
    RUNNING,
    /** 解析成功并物化进受控来源。 */
    SUCCEEDED,
    /** 解析失败，诚实记原因，不产片段（FR-5）。 */
    FAILED
}
