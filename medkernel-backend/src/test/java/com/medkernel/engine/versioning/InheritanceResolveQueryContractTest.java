package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class InheritanceResolveQueryContractTest {

    @Test
    void queryCarriesExplicitResolutionTimeWhileKeepingCurrentTimeConvenienceConstructor()
            throws Exception {
        Instant effectiveAt = Instant.parse("2026-06-01T08:00:00Z");

        InheritanceResolveQuery explicit = InheritanceResolveQuery.class
            .getDeclaredConstructor(
                String.class,
                VersionedAssetType.class,
                String.class,
                String.class,
                String.class,
                Instant.class)
            .newInstance(
                "tenant-A",
                VersionedAssetType.RULE,
                "RULE.AF",
                "specialty=AF;scenario=S16",
                "dept-1",
                effectiveAt);
        InheritanceResolveQuery current = new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "specialty=AF;scenario=S16",
            "dept-1");

        assertThat(explicit.effectiveAt()).isEqualTo(effectiveAt);
        assertThat(current.effectiveAt()).isNull();
    }
}
