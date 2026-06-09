package com.medkernel.engine.versioning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 结构化发布放量策略。
 */
public record RolloutPolicy(
    @NotNull RolloutStrategy strategy,
    List<@Size(max = 64) String> orgUnitIds,
    @Min(1) @Max(99) Integer bedPercent,
    List<@Min(1) @Max(100) Integer> stages,
    @Positive Integer observationMinutes,
    @Valid RolloutThresholds thresholds
) {
    public RolloutPolicy {
        orgUnitIds = orgUnitIds == null ? List.of() : List.copyOf(orgUnitIds);
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public static RolloutPolicy all() {
        return new RolloutPolicy(RolloutStrategy.ALL, List.of(), null, List.of(), null, null);
    }

    public static RolloutPolicy canaryBedPercent(int bedPercent) {
        return new RolloutPolicy(
            RolloutStrategy.CANARY_BED_PERCENT,
            List.of(),
            bedPercent,
            List.of(),
            null,
            null
        );
    }
}
