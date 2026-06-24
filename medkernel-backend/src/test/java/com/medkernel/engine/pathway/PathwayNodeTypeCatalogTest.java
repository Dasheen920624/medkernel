package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PathwayNodeTypeCatalogTest {

    @Test
    void onlyContainsSelfContainedPathwayNodes() {
        assertThat(Arrays.stream(PathwayNodeType.values()).map(Enum::name))
            .doesNotContain("SUBPATHWAY");
    }
}
