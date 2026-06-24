package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medkernel.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;

class AssetVersionNumbersTest {

    @Test
    void parsesAutomaticVnVersionNumbers() {
        assertThat(AssetVersionNumbers.sequence("V2", "规则资产版本")).isEqualTo(2L);
        assertThat(AssetVersionNumbers.sequence("v3", "路径资产版本")).isEqualTo(3L);
        assertThat(AssetVersionNumbers.sequence("4", "旧导入资产版本")).isEqualTo(4L);
        assertThat(AssetVersionNumbers.intSequence("V5", "评价指标版本")).isEqualTo(5);
    }

    @Test
    void rejectsBlankOrNonNumericVersionNumbers() {
        assertThatThrownBy(() -> AssetVersionNumbers.sequence("  ", "规则资产版本"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("规则资产版本不能为空");
        assertThatThrownBy(() -> AssetVersionNumbers.sequence("Vx", "规则资产版本"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("规则资产版本必须是自动分配的 Vn");
    }
}
