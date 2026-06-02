package com.medkernel.engine.clinical.model;

import com.medkernel.shared.ids.Ulid;

/**
 * SYS-01 标准临床对象 ULID 生成器。
 */
public final class ClinicalIds {

    private ClinicalIds() {}

    public static String newUlid() {
        return Ulid.newUlid();
    }
}
