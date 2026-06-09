package com.medkernel.engine.knowledge;

/**
 * 知识版本转换类型。对应 {@code knowledge_supersession.transition_type} CHECK 约束。
 *
 * <p>用于回放和 lineage：每一次状态变动都留下记录。
 */
public enum SupersessionType {
    /** 首次激活：没有前序版本，仅 new_version_id 填值 */
    ACTIVATE,
    /** 替换：旧 ACTIVE → 新 ACTIVE，两个 version_id 都填 */
    REPLACE,
    /** 撤回：将 ACTIVE 直接降为 WITHDRAWN，可能没有 new_version_id */
    WITHDRAW,
    /** 还原：撤回后又重新生效（罕见） */
    RESTORE,
    /** 回滚：紧急回退到旧版本 */
    ROLLBACK,
    /** 弃用：指定后继身份并进入迁移宽限期。 */
    DEPRECATE,
    /** 退役：宽限期结束，旧身份和租户覆盖停止参与解析。 */
    RETIRE
}
