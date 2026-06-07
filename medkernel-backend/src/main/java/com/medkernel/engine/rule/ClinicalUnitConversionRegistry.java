package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 临床单位安全换算注册表。
 *
 * <p>仅声明已核准的 UCUM 子集换算关系；不存在安全换算关系时必须拒绝，禁止按字符串或经验值猜测。
 */
final class ClinicalUnitConversionRegistry {

    private static final BigDecimal GLUCOSE_MG_DL_PER_MMOL_L = new BigDecimal("18.0182");
    private static final BigDecimal CREATININE_MG_DL_TO_UMOL_L = new BigDecimal("88.4");

    private ClinicalUnitConversionRegistry() {
    }

    static boolean sameUnit(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return normalizeUnit(left).equals(normalizeUnit(right));
    }

    static ConversionResult convert(
        String fact,
        BigDecimal value,
        String sourceUnit,
        String targetUnit,
        String analyte
    ) {
        if (sourceUnit == null || sourceUnit.isBlank()) {
            throw unitIncompatible("字段 " + fact + " 缺少单位，不能换算到 " + targetUnit);
        }
        if (sameUnit(sourceUnit, targetUnit)) {
            return new ConversionResult(value, formatNumber(value) + " " + sourceUnit);
        }
        String normalizedAnalyte = analyte.trim().toLowerCase(Locale.ROOT);
        String from = normalizeUnit(sourceUnit);
        String to = normalizeUnit(targetUnit);
        if ("glucose".equals(normalizedAnalyte) && "mg/dl".equals(from) && "mmol/l".equals(to)) {
            BigDecimal converted = value.divide(GLUCOSE_MG_DL_PER_MMOL_L, 8, RoundingMode.HALF_UP);
            return divided(value, sourceUnit, converted, GLUCOSE_MG_DL_PER_MMOL_L, targetUnit);
        }
        if ("glucose".equals(normalizedAnalyte) && "mmol/l".equals(from) && "mg/dl".equals(to)) {
            BigDecimal converted = value.multiply(GLUCOSE_MG_DL_PER_MMOL_L);
            return multiplied(value, sourceUnit, converted, GLUCOSE_MG_DL_PER_MMOL_L, targetUnit);
        }
        if ("creatinine".equals(normalizedAnalyte) && "mg/dl".equals(from) && "umol/l".equals(to)) {
            BigDecimal converted = value.multiply(CREATININE_MG_DL_TO_UMOL_L);
            return multiplied(value, sourceUnit, converted, CREATININE_MG_DL_TO_UMOL_L, targetUnit);
        }
        if ("creatinine".equals(normalizedAnalyte) && "umol/l".equals(from) && "mg/dl".equals(to)) {
            BigDecimal converted = value.divide(CREATININE_MG_DL_TO_UMOL_L, 8, RoundingMode.HALF_UP);
            return divided(value, sourceUnit, converted, CREATININE_MG_DL_TO_UMOL_L, targetUnit);
        }
        throw unitIncompatible("字段 " + fact + " 不存在 " + analyte + " 的 "
            + sourceUnit + " 到 " + targetUnit + " 安全换算关系");
    }

    private static ConversionResult divided(
        BigDecimal sourceValue,
        String sourceUnit,
        BigDecimal converted,
        BigDecimal factor,
        String targetUnit
    ) {
        return new ConversionResult(converted,
            formatNumber(sourceValue) + " " + sourceUnit
                + " / " + formatNumber(factor)
                + " = " + formatNumber(round2(converted)) + " " + targetUnit);
    }

    private static ConversionResult multiplied(
        BigDecimal sourceValue,
        String sourceUnit,
        BigDecimal converted,
        BigDecimal factor,
        String targetUnit
    ) {
        return new ConversionResult(converted,
            formatNumber(sourceValue) + " " + sourceUnit
                + " * " + formatNumber(factor)
                + " = " + formatNumber(round2(converted)) + " " + targetUnit);
    }

    private static String normalizeUnit(String unit) {
        return unit.trim()
            .replace("μ", "u")
            .replace("µ", "u")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }

    private static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static ApiException unitIncompatible(String message) {
        return new ApiException(ErrorCode.UNIT_INCOMPATIBLE, message);
    }

    record ConversionResult(BigDecimal value, String formula) {
    }
}
