package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

/**
 * 引擎数据服务层 · 隐私分级策略服务单元测试（DATASVC-01 PR2-c）。
 *
 * <p>验证数据分级 D0–D5 准入策略（FR-2）：D2 准入；D3/D4 在字段级加密能力就绪后准入且标记必须加密；
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
    void validate_d3_allowedWithFieldEncryptionRequired() {
        PrivacyPolicyDecision decision = service.validate("D3");

        // T6.4 后 D3/D4 已具备字段级加密落库能力，可准入但必须继续强制加密。
        assertThat(decision.allowed()).isTrue();
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
