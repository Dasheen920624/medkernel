package com.medkernel.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将首登强制改密守卫挂到 MVC 控制器入口。
 */
@Configuration
public class CredentialBootstrapGuardMvcConfiguration implements WebMvcConfigurer {

    private final CredentialBootstrapGuardInterceptor interceptor;

    public CredentialBootstrapGuardMvcConfiguration(CredentialBootstrapGuardInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
