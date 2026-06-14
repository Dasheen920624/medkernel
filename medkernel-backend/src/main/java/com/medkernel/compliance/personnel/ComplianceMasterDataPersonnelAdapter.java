package com.medkernel.compliance.personnel;

import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.medkernel.compliance.identitybinding.IdentityProviderType;
import com.medkernel.engine.integration.masterdata.MasterDataPersonCommand;
import com.medkernel.engine.integration.masterdata.MasterDataPersonnelPort;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 将引擎集成域的中立人员命令适配到合规人员主数据归属域。
 */
@Component
public class ComplianceMasterDataPersonnelAdapter implements MasterDataPersonnelPort {

    private final PersonnelService personnel;

    public ComplianceMasterDataPersonnelAdapter(PersonnelService personnel) {
        this.personnel = personnel;
    }

    @Override
    public String upsert(MasterDataPersonCommand command, Authentication authentication) {
        return personnel.syncFromExternal(new PersonnelSyncCommand(
            command.employeeNo(),
            command.displayName(),
            command.organizationCode(),
            command.departmentCode(),
            command.wardCode(),
            requiredEnum(AppointmentType.class, command.appointmentType(), "任职类型"),
            command.positionTitle(),
            command.userId(),
            command.roleCode(),
            optionalEnum(IdentityProviderType.class, command.identityProvider(), "身份来源"),
            command.identitySubject(),
            optionalEnum(PersonStatus.class, command.status(), "人员状态"),
            false), authentication);
    }

    @Override
    public void disable(String internalId, Authentication authentication) {
        personnel.disableFromExternal(internalId, authentication);
    }

    private <T extends Enum<T>> T requiredEnum(
            Class<T> type,
            String value,
            String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "人员主数据缺少" + label);
        }
        return parseEnum(type, value, label);
    }

    private <T extends Enum<T>> T optionalEnum(
            Class<T> type,
            String value,
            String label) {
        return value == null || value.isBlank() ? null : parseEnum(type, value, label);
    }

    private <T extends Enum<T>> T parseEnum(
            Class<T> type,
            String value,
            String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "人员主数据" + label + "不合法: " + value,
                exception);
        }
    }
}
