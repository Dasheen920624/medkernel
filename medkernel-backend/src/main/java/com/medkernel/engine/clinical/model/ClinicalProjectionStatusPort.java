package com.medkernel.engine.clinical.model;

/**
 * 查询标准临床对象图投影状态的端口。
 */
@FunctionalInterface
public interface ClinicalProjectionStatusPort {

    ClinicalProjectionStatus status(String tenantId);
}
