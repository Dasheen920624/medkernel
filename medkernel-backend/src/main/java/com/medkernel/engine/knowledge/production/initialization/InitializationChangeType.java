package com.medkernel.engine.knowledge.production.initialization;

/** canonical 资产语义版本变化类型。 */
public enum InitializationChangeType {
    NEW,
    PATCH_COMPATIBLE,
    MINOR_COMPATIBLE,
    MAJOR_BREAKING,
    DEPRECATION
}
