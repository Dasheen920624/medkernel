package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 登录前租户字典服务。
 *
 * <p>客户 / 集团租户存在时优先作为登录第一层；平台主租户永远唯一，退到第二层给平台接管和服务运行保障人员使用。
 */
@Service
public class LoginTenantDirectoryService {

    private static final String KIND_PLATFORM = "PLATFORM";
    private static final String KIND_CUSTOMER = "CUSTOMER";

    private final OrgUnitRepository orgUnits;

    public LoginTenantDirectoryService(OrgUnitRepository orgUnits) {
        this.orgUnits = orgUnits;
    }

    public LoginTenantDirectoryResponse directory() {
        LoginTenantOption platform = new LoginTenantOption(
            PlatformTenant.ID, PlatformTenant.DISPLAY_NAME, KIND_PLATFORM);
        List<LoginTenantOption> customers = orgUnits.findAllTenantRoots().stream()
            .filter(unit -> unit.status() == OrgUnitStatus.ACTIVE)
            .filter(unit -> !PlatformTenant.isPlatformTenant(unit.tenantId()))
            .map(unit -> new LoginTenantOption(unit.tenantId(), unit.name(), KIND_CUSTOMER))
            .toList();
        if (customers.isEmpty()) {
            return new LoginTenantDirectoryResponse(List.of(platform), platform, false);
        }
        return new LoginTenantDirectoryResponse(customers, platform, true);
    }
}
