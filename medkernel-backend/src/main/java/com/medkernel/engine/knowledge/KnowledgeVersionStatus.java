package com.medkernel.engine.knowledge;

/**
 * 知识资产版本状态机。对应 {@code knowledge_asset_version.status} CHECK 约束。
 *
 * <p>状态机（详细规范 §1.4 / §1797-1806）：
 * <pre>
 *   DRAFT ──编辑──&gt; CANDIDATE ──新旧识别──&gt; PENDING_REPLACEMENT_REVIEW ──审核通过──&gt; ACTIVE ──新版替代─&gt; SUPERSEDED
 *                                 └──────提交审核──&gt; UNDER_REVIEW ───────┘                       └──紧急撤回─&gt; WITHDRAWN
 *                                                          └─审核驳回─&gt; REJECTED
 * </pre>
 *
 * <p>关键不变量：同一 {@code identity_id} 同时刻 {@link #ACTIVE} 版本 ≤ 1（由 Service 层事务保证）。
 */
public enum KnowledgeVersionStatus {
    /** 编辑中草稿，不进入审核台 */
    DRAFT,
    /** AI 生成或人工录入的候选，等待审核分流 */
    CANDIDATE,
    /** 新旧识别后的替换候选，仅供人工对照审核，不参与临床执行 */
    PENDING_REPLACEMENT_REVIEW,
    /** 发布依据核对中。 */
    UNDER_REVIEW,
    /** 当前权威版本：临床决策、规则校验、推荐均以此版本为准 */
    ACTIVE,
    /** 已被新版本替代，旧版进入"历史版本"时间轴，不再参与新临床决策 */
    SUPERSEDED,
    /** 紧急撤回（合规风险、严重错误、上游召回）。比 SUPERSEDED 更高优先级 */
    WITHDRAWN,
    /** 审核驳回，不会进入 ACTIVE；可改回 DRAFT 继续完善 */
    REJECTED;

    /** 该状态版本是否可被审核激活 */
    public boolean isActivatable() {
        return this == UNDER_REVIEW || this == CANDIDATE || this == PENDING_REPLACEMENT_REVIEW
            || this == SUPERSEDED;
    }

    /** 该状态版本是否当前可作为临床决策依据 */
    public boolean isAuthoritative() {
        return this == ACTIVE;
    }

    /** 该状态版本是否已退出新的临床决策 */
    public boolean isRetired() {
        return this == SUPERSEDED || this == WITHDRAWN;
    }
}
