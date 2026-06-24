package com.medkernel.engine.sandbox;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 读取当前机构生效版本的沙盘就绪状态，不维护第二套激活绑定。 */
@Service
public class SandboxRuntimeStatusService {

    private final SandboxRuntimeBaselineResolver baselines;

    public SandboxRuntimeStatusService(SandboxRuntimeBaselineResolver baselines) {
        this.baselines = baselines;
    }

    public SandboxRuntimeStatusResponse currentStatus() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String targetOrgUnitId = scope.nearestOrgUnitIdOrTenant(scope.tenantId());
        try {
            return SandboxRuntimeStatusResponse.ready(baselines.resolveCurrent());
        } catch (RuntimeException exception) {
            String message = messageOf(exception);
            return SandboxRuntimeStatusResponse.notReady(
                targetOrgUnitId, reasonCode(message), message);
        }
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }

    private static String reasonCode(String message) {
        int separator = message.indexOf('：');
        if (separator < 0) {
            separator = message.indexOf(':');
        }
        String candidate = separator < 0 ? message : message.substring(0, separator);
        return candidate.matches("[A-Z0-9_]+") ? candidate : "SANDBOX_RUNTIME_NOT_READY";
    }
}
