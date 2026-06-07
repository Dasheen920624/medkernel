package com.medkernel.engine.pathway;

/**
 * 路径变异类型。
 *
 * <p>按质控统计口径区分临床、系统、患者和家属四类路径偏离。
 */
public enum VarianceType {
    CLINICAL,
    SYSTEM,
    PATIENT,
    FAMILY
}
