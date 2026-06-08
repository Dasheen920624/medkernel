package com.medkernel.engine.org;

import java.time.Instant;

/**
 * 组织次级归属边。
 *
 * <p>用于表达病区同时归属科室与专科中心、共享中心同时服务多个机构等矩阵关系；
 * 主父链仍由 {@link OrgUnit#parentId()} 与 {@link OrgUnit#orgPath()} 表达。
 */
public record OrgSecondaryMembership(
    String tenantId,
    String childId,
    String secondaryParentId,
    String relationCode,
    int priority,
    Instant createdAt,
    String createdBy
) {}
