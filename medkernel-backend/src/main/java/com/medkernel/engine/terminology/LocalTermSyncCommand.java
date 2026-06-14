package com.medkernel.engine.terminology;

/**
 * 院内业务系统本地术语同步命令。
 */
public record LocalTermSyncCommand(
    String sourceSystem,
    String localCode,
    String category,
    String localName,
    String normalizedName,
    String departmentCode,
    String status,
    boolean disable
) {
}
