package com.medkernel.engine.integration.masterdata;

/**
 * 引擎集成域向人员主数据归属域提交的中立同步命令。
 *
 * <p>枚举值保持为外部契约字符串，由归属域适配器完成类型转换，避免引擎反向依赖业务实现。
 */
public record MasterDataPersonCommand(
    String employeeNo,
    String displayName,
    String organizationCode,
    String departmentCode,
    String wardCode,
    String appointmentType,
    String positionTitle,
    String userId,
    String roleCode,
    String identityProvider,
    String identitySubject,
    String status
) {
}
