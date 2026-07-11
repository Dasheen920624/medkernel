package com.medkernel.shared.web;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * MedKernel v1.0 GA · 基础心跳端点。
 *
 * <p>真实健康检查由 Spring Boot Actuator 提供（{@code /actuator/health}）；
 * 本端点提供"产品宪法生效中"的极简心跳，并作为 GA-ENG-BASE-03 标准 ApiResult 范例。
 *
 * <p>对外契约示例：
 * <pre>{@code
 * GET /api/v1/system/ping
 * → {
 *     "success": true, "code": "OK", "message": "操作成功",
 *     "data": { "product": "MedKernel", "version": "1.0.0-SNAPSHOT", ... },
 *     "traceId": "...", "timestamp": "..."
 *   }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final String version;
    private final String stage;
    private final BuildIdentityResolver buildIdentityResolver;

    public HealthController(@Value("${medkernel.version:1.0.0-SNAPSHOT}") String version,
                            @Value("${medkernel.stage:v1.0 GA · 0 业务引擎全能力上线}") String stage,
                            BuildIdentityResolver buildIdentityResolver) {
        this.version = version;
        this.stage = stage;
        this.buildIdentityResolver = buildIdentityResolver;
    }

    @GetMapping("/ping")
    public ApiResult<PingResponse> ping() {
        BuildIdentity buildIdentity = buildIdentityResolver.resolve();
        return ApiResult.ok(new PingResponse(
            "MedKernel",
            version,
            stage,
            buildIdentity.bound(),
            buildIdentity.candidateCommit(),
            Instant.now()
        ));
    }
}
