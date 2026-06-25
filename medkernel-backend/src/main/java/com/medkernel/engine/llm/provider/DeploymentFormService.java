package com.medkernel.engine.llm.provider;

import org.springframework.stereotype.Service;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 部署形态服务（LLM-08 双形态）。
 *
 * <p>从配置中心读取 {@code medkernel.deployment.form}，裁决本实例是否允许 B2 外部 provider 出域。
 * 公网形态允许外部 provider，但所有患者上下文必须先过模型外调安全闸；运行侧（内网医院）禁外部
 * provider，只走本地模型 B1 / B0。
 */
@Service
public class DeploymentFormService {

    private final SystemConfigService configService;

    public DeploymentFormService(SystemConfigService configService) {
        this.configService = configService;
    }

    public DeploymentForm currentForm() {
        return DeploymentForm.fromConfig(configService.runtimeDeploymentForm());
    }

    /**
     * 是否允许 B2 外部 provider（Claude / OpenAI 兼容 API 等）：仅公网/生产中心形态允许。
     */
    public boolean allowsExternalProvider() {
        return currentForm() == DeploymentForm.PRODUCTION_CENTER;
    }
}
