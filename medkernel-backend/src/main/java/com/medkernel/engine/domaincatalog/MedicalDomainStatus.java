package com.medkernel.engine.domaincatalog;

/**
 * 医疗领域目录状态。
 */
public enum MedicalDomainStatus {
    /** 可用于新资产归类。 */
    ACTIVE,
    /** 保留历史关系，但不允许新建归类。 */
    INACTIVE
}
