package com.medkernel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 启动入口判定测试：决定何时以非 Web 模式启动救命通道。
 *
 * <p>首发身份应急命令（{@code --bootstrap-emergency=...}）必须以非 Web 模式旁路启动，
 * 避免与已占用业务端口的生产实例冲突；其余正常启动保持默认 Web 应用类型。
 */
class MedKernelApplicationTest {

    @Test
    void detectsEmergencyCommandWithValue() {
        assertThat(MedKernelApplication.isEmergencyCommand(new String[] {
            "--bootstrap-emergency=mfa-reset", "--tenant-id=t-1"
        })).isTrue();
    }

    @Test
    void detectsBareEmergencyOption() {
        assertThat(MedKernelApplication.isEmergencyCommand(new String[] {
            "--bootstrap-emergency"
        })).isTrue();
    }

    @Test
    void normalStartupIsNotEmergency() {
        assertThat(MedKernelApplication.isEmergencyCommand(new String[] {
            "--server.port=18080", "--spring.profiles.active=govcloud"
        })).isFalse();
    }

    @Test
    void emptyArgsIsNotEmergency() {
        assertThat(MedKernelApplication.isEmergencyCommand(new String[] {})).isFalse();
    }
}
