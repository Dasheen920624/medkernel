package com.medkernel.engine.contract;

import com.medkernel.shared.audit.AuditAction;

/**
 * 服务契约中的审计点声明。
 *
 * @param action 审计动作
 * @param targetType 审计目标类型
 * @param purpose 审计点覆盖的业务动作
 */
public record ServiceAuditDeclaration(
    AuditAction action,
    String targetType,
    String purpose
) {}
