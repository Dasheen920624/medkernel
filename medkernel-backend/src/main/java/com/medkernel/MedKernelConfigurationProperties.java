package com.medkernel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.medkernel.engine.cdshook.RealtimeCdsProperties;
import com.medkernel.engine.context.ClinicalEventProperties;
import com.medkernel.engine.integration.service.IntegrationProperties;
import com.medkernel.shared.idempotency.IdempotencyProperties;

/**
 * 构造器式运行配置属性统一注册点，确保外部配置真实绑定到不可变属性对象。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    ClinicalEventProperties.class,
    IntegrationProperties.class,
    RealtimeCdsProperties.class,
    IdempotencyProperties.class
})
public class MedKernelConfigurationProperties {
}
