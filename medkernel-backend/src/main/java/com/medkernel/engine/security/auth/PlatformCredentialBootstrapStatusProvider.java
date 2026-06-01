package com.medkernel.engine.security.auth;

import org.springframework.stereotype.Component;

import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.security.CredentialBootstrapStatusProvider;

/**
 * 平台凭证首登安全状态提供者，供 shared 安全过滤链查询。
 */
@Component
public class PlatformCredentialBootstrapStatusProvider implements CredentialBootstrapStatusProvider {

    private final PlatformCredentialRepository credentials;

    public PlatformCredentialBootstrapStatusProvider(PlatformCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean mustChangePassword(String tenantId, String userId) {
        return credentials.findByTenantIdAndUserId(tenantId, userId)
            .map(credential -> "Y".equalsIgnoreCase(credential.mustChangePwd()))
            .orElse(false);
    }
}
