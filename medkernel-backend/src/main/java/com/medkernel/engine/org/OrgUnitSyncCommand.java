package com.medkernel.engine.org;

import com.medkernel.shared.context.OrgLevel;

/**
 * 院内业务系统组织主数据同步命令。
 */
public record OrgUnitSyncCommand(
    String code,
    String parentCode,
    OrgLevel level,
    String name,
    String namePinyin,
    OrgFacilityType facilityType,
    String specialtyId,
    OrgUnitStatus status,
    boolean disable
) {
}
