package com.medkernel.engine.knowledge.authority;

/** 医疗资源包交付类型；完整包可以自举空医院，差量包必须绑定精确基线。 */
public enum MedicalPackageType {
    FULL,
    DELTA
}
