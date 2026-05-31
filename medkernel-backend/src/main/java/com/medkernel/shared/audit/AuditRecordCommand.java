package com.medkernel.shared.audit;

import java.util.Objects;

/**
 * 统一审计记录命令。
 *
 * <p>业务代码只描述动作、目标和变更快照；用户、角色、组织路径、traceId、时间戳
 * 由 {@link AuditRecorder} 从请求上下文统一补齐，避免各业务线自行拼审计字段。
 *
 * @param action         审计动作
 * @param targetType     被审计目标类型
 * @param targetId       被审计目标 ID
 * @param summary        审计摘要
 * @param before         变更前快照，可为 null
 * @param after          变更后快照，可为 null
 * @param environmentKey 环境标识，如 dev / staging / prod / 国产化 profile
 */
public record AuditRecordCommand(
    AuditAction action,
    String targetType,
    String targetId,
    String summary,
    Object before,
    Object after,
    String environmentKey
) {

    public AuditRecordCommand {
        Objects.requireNonNull(action, "审计动作不能为空");
        requireText(targetType, "审计目标类型不能为空");
        requireText(targetId, "审计目标 ID 不能为空");
        if (summary != null) {
            summary = summary.trim();
        }
        if (environmentKey != null) {
            environmentKey = environmentKey.trim();
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
