package com.medkernel.engine.llm.provider;

import java.util.Locale;

/**
 * 部署形态（LLM-08 双形态 · wave2 _brief §1）。
 *
 * <p>{@link #PRODUCTION_CENTER}：外网知识生产中心（只吃公开医学资料，无患者数据，可调用 B2 外部大模型服务）；
 * {@link #HOSPITAL_RUNTIME}：内网院内运行环境（碰患者数据，禁外部模型服务，仅本地模型 B1 / B0）。
 * 未知或非法配置一律回退最严格的 {@code HOSPITAL_RUNTIME}（安全默认）。
 */
public enum DeploymentForm {

    PRODUCTION_CENTER,
    HOSPITAL_RUNTIME;

    public static DeploymentForm fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return HOSPITAL_RUNTIME;
        }
        try {
            return DeploymentForm.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return HOSPITAL_RUNTIME;
        }
    }
}
