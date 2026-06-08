package com.medkernel.engine.authoring;

/**
 * 创作增强能力开关读取入口。
 */
@FunctionalInterface
public interface AuthoringFeatureGate {

    boolean enabled(AuthoringFeatureFlag flag);

    static AuthoringFeatureGate alwaysEnabled() {
        return flag -> true;
    }
}
