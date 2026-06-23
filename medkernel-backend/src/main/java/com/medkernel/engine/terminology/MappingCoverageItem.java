package com.medkernel.engine.terminology;

/**
 * 标准编码对照覆盖项。
 *
 * <p>给定规则/路径引用的标准编码，判断院内→标准对照覆盖情况，供发布前提示「哪些编码缺院内
 * 对照、真实数据可能永远无法归一命中」。advisory 先行，不阻断发布。
 *
 * @param code            标准编码
 * @param status          覆盖状态：COVERED（已有确认对照）/ UNMAPPED（标准码存在但无院内对照）/
 *                        NO_STANDARD_TERM（该标准码不在标准字典内）
 * @param mappedLocalCount 已确认的院内→标准对照数量
 */
public record MappingCoverageItem(String code, String status, int mappedLocalCount) {

    public static final String COVERED = "COVERED";
    public static final String UNMAPPED = "UNMAPPED";
    public static final String NO_STANDARD_TERM = "NO_STANDARD_TERM";

    /** 依据标准码是否存在与确认对照数量判定覆盖状态（纯函数，便于单测）。 */
    public static String classify(boolean hasStandardTerm, int confirmedMappingCount) {
        if (!hasStandardTerm) {
            return NO_STANDARD_TERM;
        }
        return confirmedMappingCount > 0 ? COVERED : UNMAPPED;
    }
}
