package com.medkernel.engine.knowledge.acquisition;

/**
 * 公域资料许可裁决。许可原文仍落 {@code license}，本字段记录治理审批后的可用性。
 */
public enum AcquisitionLicensePolicy {
    /** 已确认可用于知识生产。 */
    PERMITTED,
    /** 需人工另行授权，未授权前不得抓取。 */
    RESTRICTED,
    /** 明确禁止入库或二次使用。 */
    FORBIDDEN;

    public boolean isPermitted() {
        return this == PERMITTED;
    }
}
