package com.medkernel.engine.context;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

/**
 * 按认证组织上下文解析医院当前临床运行修订。
 *
 * <p>临床与集成调用方不得选择发布容器、领域或版本；运行修订只由服务端根据租户和医院确定。
 */
@Service
public class CurrentClinicalRuntimeReleaseResolver {

    private final ClinicalRuntimeReleaseRepository releases;

    public CurrentClinicalRuntimeReleaseResolver(
            ClinicalRuntimeReleaseRepository releases) {
        this.releases = releases;
    }

    /**
     * 返回当前医院修订号最大的不可变运行修订。
     */
    @Transactional(readOnly = true)
    public ClinicalRuntimeRelease resolve(OrgScope scope) {
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String hospitalId = required(scope.hospitalId(), "认证上下文缺少医院");
        return releases
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
                scope.tenantId().trim(),
                hospitalId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_CONTEXT_002,
                "当前医院尚未生成临床运行修订"
            ));
    }

    /**
     * 校验并返回上游已锁定的医院运行修订。
     */
    @Transactional(readOnly = true)
    public ClinicalRuntimeRelease require(OrgScope scope, String releaseId) {
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String hospitalId = required(scope.hospitalId(), "认证上下文缺少医院");
        String normalizedReleaseId = required(releaseId, "临床运行修订不能为空");
        ClinicalRuntimeRelease release = releases
            .findByTenantIdAndReleaseId(scope.tenantId().trim(), normalizedReleaseId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_CONTEXT_002,
                "临床运行修订不存在或不属于当前租户"
            ));
        if (!hospitalId.equals(release.hospitalId())) {
            throw new ApiException(
                ErrorCode.ORG_SCOPE_DENIED,
                "临床运行修订不属于当前医院"
            );
        }
        return release;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_002, message);
        }
        return value.trim();
    }
}
