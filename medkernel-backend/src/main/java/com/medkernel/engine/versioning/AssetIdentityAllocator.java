package com.medkernel.engine.versioning;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 资产稳定身份统一分配器。
 *
 * <p>平台与租户资产使用互斥命名空间，显示名称和业务编码变更不会改变稳定身份。
 */
@Component
public class AssetIdentityAllocator {

    private static final int MAX_IDENTITY_LENGTH = 128;
    private static final Pattern TENANT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern DOMAIN = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public String allocate(String tenantId, String domain, String slug) {
        String normalizedTenantId = requireMatch(tenantId, TENANT_ID, "租户 ID");
        String normalizedDomain = requireMatch(domain, DOMAIN, "资产域")
            .toLowerCase(Locale.ROOT);
        String normalizedSlug = requireMatch(slug, SLUG, "资产身份 slug");
        String identity;
        if (PlatformTenant.isPlatformTenant(normalizedTenantId)) {
            identity = "plat:" + normalizedDomain + ":" + normalizedSlug;
        } else {
            identity = "t:" + normalizedTenantId + ":" + normalizedDomain + ":" + normalizedSlug;
        }
        if (identity.length() > MAX_IDENTITY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产稳定身份超过 128 字符上限");
        }
        return identity;
    }

    private String requireMatch(String raw, Pattern pattern, String label) {
        if (raw == null || raw.isBlank() || !pattern.matcher(raw.trim()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "格式不合法");
        }
        return raw.trim();
    }
}
