package com.medkernel.shared.audit;

/**
 * 统一审计动作枚举。
 *
 * <p>对应宪法 §1 第 4 条 "审计上下文：写操作、审核、发布、运行、反馈、导出、回滚统一留痕"。
 * 任何新业务动作必须复用本枚举，禁止使用裸字符串。
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    REVIEW,
    PUBLISH,
    EXECUTE,
    FEEDBACK,
    EXPORT,
    IMPORT,
    ROLLBACK,
    LOGIN,
    LOGOUT,
    PERMISSION_CHANGE
}
