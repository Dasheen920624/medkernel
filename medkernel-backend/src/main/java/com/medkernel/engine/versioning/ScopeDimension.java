package com.medkernel.engine.versioning;

import java.util.Arrays;
import java.util.Optional;

/**
 * 配置资产适用范围的横切维度。
 */
public enum ScopeDimension {
    SPECIALTY("specialty"),
    SCENARIO("scenario"),
    CARE_SETTING("setting"),
    COHORT("cohort"),
    ROLE("role");

    private final String wireName;

    ScopeDimension(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<ScopeDimension> fromWireName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
            .filter(dimension -> dimension.wireName.equalsIgnoreCase(normalized))
            .findFirst();
    }
}
