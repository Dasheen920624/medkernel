package com.medkernel.engine.release;

import java.time.Instant;
import java.util.List;

/**
 * 机构生效版本离线交付文件规范化快照。
 *
 * <p>该文件只用于完整性校验和导入预检，不作为临床运行指针。
 */
public record RuntimeReleaseOfflineDeliverySnapshot(
    String schemaVersion,
    String deliveryKind,
    boolean runtimeMutation,
    Instant exportedAt,
    String exportedBy,
    String traceId,
    ClinicalRuntimeReleaseOfflineSnapshot release,
    List<ClinicalRuntimeReleaseItemOfflineSnapshot> items,
    String warning
) {
    public RuntimeReleaseOfflineDeliverySnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
