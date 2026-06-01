package com.medkernel.engine.clinical.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认图投影状态端口：未接入真实图投影时诚实返回 NOT_SYNCED。
 */
@Component
@ConditionalOnMissingBean(ClinicalProjectionStatusPort.class)
public class NoopClinicalProjectionStatusPort implements ClinicalProjectionStatusPort {

    @Override
    public ClinicalProjectionStatus status(String tenantId) {
        return ClinicalProjectionStatus.NOT_SYNCED;
    }
}
