package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/**
 * 应急命令救命通道前置门禁：应用必须能在**非 Web 模式**（{@code web-application-type=none}）下完整启动。
 *
 * <p>背景：初始身份应急命令（MFA 重置 / 解锁）是 {@link BootstrapEmergencyCommand} 这个
 * {@link org.springframework.boot.ApplicationRunner}，只有在 Spring 上下文成功启动后才会执行。
 * 真实部署中生产实例已占用业务端口，救命通道必须以非 Web 模式旁路启动、不绑定端口。
 *
 * <p>回归 2026-06-10 现场缺陷：{@code SecurityConfig.filterChain(HttpSecurity ...)} 等
 * servlet Web 专属 Bean 未按 Web 应用类型设条件，非 Web 启动时因缺 {@code HttpSecurity} 直接崩，
 * 上下文起不来、ApplicationRunner 永不执行，救命通道形同虚设（曾被迫用占空业务端口的变通绕过）。
 *
 * <p>本测试以 {@code WebEnvironment.NONE} 启动全量上下文（H2，无 Docker 依赖，CI 永远跑），
 * 证明非 Web 模式可启动且应急命令已装配；任何新增 Web 专属 Bean 漏设条件都会让本测试变红。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BootstrapEmergencyNonWebBootTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BootstrapEmergencyCommand emergencyCommand;

    @Test
    void applicationBootsWithoutServletWebContext() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }

    @Test
    void emergencyCommandIsWiredForRescueChannel() {
        assertThat(emergencyCommand).isNotNull();
    }
}
