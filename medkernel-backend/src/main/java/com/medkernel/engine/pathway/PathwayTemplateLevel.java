package com.medkernel.engine.pathway;

/**
 * 临床路径层级。
 *
 * <p>标识临床路径是标准版、集团版、医院版、科室版还是专科版，便于后续多层级治理。
 */
public enum PathwayTemplateLevel {
    STANDARD,
    GROUP,
    HOSPITAL,
    DEPARTMENT,
    SPECIALTY
}
