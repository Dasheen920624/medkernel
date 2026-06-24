package com.medkernel.engine.release;

/**
 * 机构生效版本中资产的来源层级。
 */
public enum ReleaseSourceLayer {
    /** 平台权威资产。 */
    PLATFORM,
    /** 集团范围定制资产。 */
    GROUP,
    /** 医院本地定制资产或院内映射。 */
    HOSPITAL
}
