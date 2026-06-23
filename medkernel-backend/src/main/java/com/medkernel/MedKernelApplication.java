package com.medkernel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedKernelApplication {

    /** 初始身份应急命令选项前缀；命中即以非 Web 模式旁路启动救命通道。 */
    private static final String EMERGENCY_OPTION = "--bootstrap-emergency";

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MedKernelApplication.class);
        if (isEmergencyCommand(args)) {
            // 救命通道：生产实例已占用业务端口，应急命令必须以非 Web 模式启动、不绑定端口，
            // 否则端口冲突会让上下文起不来、ApplicationRunner 永不执行。即便部署脚本漏传
            // --spring.main.web-application-type=none，这里也强制兜底。
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }

    /**
     * 判断启动参数是否为初始身份应急命令（{@code --bootstrap-emergency} 或 {@code --bootstrap-emergency=...}）。
     */
    static boolean isEmergencyCommand(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (arg != null
                    && (arg.equals(EMERGENCY_OPTION) || arg.startsWith(EMERGENCY_OPTION + "="))) {
                return true;
            }
        }
        return false;
    }
}
