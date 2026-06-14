package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

/**
 * 引擎数据服务层 · 隐私分级策略服务单元测试（DATASVC-01 PR2-c）。
 *
 * <p>验证数据分级 D0–D5 准入策略（FR-2）：D2 准入；D3/D4 因字段级加密未实现诚实判不准入；
 * D5 重要个人信息禁入；非法级别结构化拒绝不泄漏内部。
 */
class PrivacyPolicyServiceTest {

    private final PrivacyPolicyService service = new PrivacyPolicyService();

    @Test
    void validate_d2_allowedWithoutEncryption() {
        PrivacyPolicyDecision decision = service.validate("D2");

        assertThat(decision.requestedLevel()).isEqualTo("D2");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.requiresFieldEncryption()).isFalse();
        assertThat(decision.dataLevel()).isEqualTo(EngineDataLevel.D0);
    }

    @Test
    void validate_d3_deniedBecauseFieldEncryptionNotImplemented() {
        PrivacyPolicyDecision decision = service.validate("D3");

        // D3 须字段级加密，当前未实现故诚实判不准入（不以「已支持」伪装，铁律 #1）。
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.requiresFieldEncryption()).isTrue();
        assertThat(decision.reason()).contains("字段级加密");
    }

    @Test
    void validate_d5_deniedAsRestrictedPersonalInformation() {
        PrivacyPolicyDecision decision = service.validate("D5");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("禁入");
    }

    @Test
    void validate_invalidLevel_throwsStructuredErrorNotLeakingInternals() {
        assertThatThrownBy(() -> service.validate("D9"))
            .isInstanceOf(ApiException.class)
            .hasMessageNotContaining("Exception");
    }
}
