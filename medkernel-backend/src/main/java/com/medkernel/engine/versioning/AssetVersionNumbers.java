package com.medkernel.engine.versioning;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 统一资产版本号解析工具。
 *
 * <p>统一资产底座分配的正式形态为 {@code V1}、{@code V2}、{@code V3}……；
 * 为支持清洁基线导入和历史裸整数收敛，运行侧允许读取裸整数，但禁止业务代码各自解析。
 */
public final class AssetVersionNumbers {

    private AssetVersionNumbers() {
    }

    public static long sequence(String versionNo, String label) {
        String normalized = required(versionNo, label);
        String digits = normalized.matches("[Vv]\\d+")
            ? normalized.substring(1)
            : normalized;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                label + "必须是自动分配的 Vn: " + versionNo,
                exception
            );
        }
    }

    public static int intSequence(String versionNo, String label) {
        long sequence = sequence(versionNo, label);
        if (sequence > Integer.MAX_VALUE) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                label + "超出整数范围: " + versionNo
            );
        }
        return (int) sequence;
    }

    public static String canonical(int versionNo) {
        if (versionNo <= 0) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "资产版本序号必须大于 0: " + versionNo
            );
        }
        return "V" + versionNo;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                label + "不能为空"
            );
        }
        return value.trim();
    }
}
