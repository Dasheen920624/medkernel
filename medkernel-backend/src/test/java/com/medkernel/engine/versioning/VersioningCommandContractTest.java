package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class VersioningCommandContractTest {

    @Test
    void assetVersionRequiresExplicitSafetyAndOverridePolicies() {
        assertThat(publicConstructors(AssetVersion.class)).hasSize(1);
        assertThat(publicConstructors(AssetVersion.class)[0].getParameterTypes())
            .containsSubsequence(AssetVersionSafetyPolicy.class, AssetVersionOverridePolicy.class);
    }

    @Test
    void inheritanceOverrideRequiresExplicitPropagation() {
        assertThat(publicConstructors(InheritanceOverrideRegisterCommand.class)).hasSize(1);
        assertThat(publicConstructors(InheritanceOverrideRegisterCommand.class)[0].getParameterTypes())
            .endsWith(InheritancePropagation.class);
    }

    private Constructor<?>[] publicConstructors(Class<?> type) {
        return Arrays.stream(type.getConstructors())
            .filter(constructor -> !constructor.isSynthetic())
            .toArray(Constructor<?>[]::new);
    }
}
