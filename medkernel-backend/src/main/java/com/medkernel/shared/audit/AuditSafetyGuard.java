package com.medkernel.shared.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 审计高危配置护栏。
 *
 * <p>审计持久化属于宪法 #19 高危项，任何 UI / 配置中心尝试关闭都必须被拒绝，
 * 且拒绝动作本身也要进入统一审计入口。
 */
@Component
public class AuditSafetyGuard {

    private static final Set<String> PROTECTED_AUDIT_SWITCHES = Set.of(
        "medkernel.audit.persistence.enabled",
        "medkernel.audit.enabled",
        "audit.persistence.enabled",
        "audit.enabled");

    private static final Set<String> DISABLED_VALUES = Set.of(
        "false", "0", "off", "disabled", "disable", "no", "n");

    private final AuditRecorder recorder;

    public AuditSafetyGuard(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    public void assertChangeAllowed(AuditConfigChangeCommand command) {
        if (!isProtectedAuditSwitch(command.key()) || !isDisablingValue(command.afterValue())) {
            return;
        }
        recorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "audit_config",
            command.key(),
            "拒绝关闭审计持久化：" + command.key(),
            snapshot(command.beforeValue(), command.reason()),
            snapshot(command.afterValue(), command.reason()),
            null));
        throw new ApiException(ErrorCode.ENG_AUDIT_001);
    }

    private static boolean isProtectedAuditSwitch(String key) {
        return key != null && PROTECTED_AUDIT_SWITCHES.contains(key.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isDisablingValue(String value) {
        if (value == null) {
            return false;
        }
        return DISABLED_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> snapshot(String value, String reason) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("value", value);
        if (reason != null) {
            result.put("reason", reason);
        }
        return result;
    }
}
