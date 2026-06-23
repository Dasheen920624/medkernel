package com.medkernel.engine.terminology;

/**
 * 已通过术语包全量激活的不可变映射结果。
 *
 * @param mappingId 正式映射 ID
 * @param standardTermId 标准术语 ID
 * @param standardCode 构包时固化的标准编码
 * @param versionNo 医院运行修订锁定的术语资产版本号
 */
public record EffectiveTermMapping(
    Long mappingId,
    Long standardTermId,
    String standardCode,
    String versionNo
) {
}
