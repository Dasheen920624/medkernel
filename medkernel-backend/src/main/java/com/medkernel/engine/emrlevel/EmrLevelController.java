package com.medkernel.engine.emrlevel;

import java.util.List;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EMR-LEVEL-01 电子病历评级目标与项目映射 API。
 */
@RestController
@RequestMapping("/api/v1/engine/emr-level")
@DataScope(requireTenant = true)
public class EmrLevelController {
    private final EmrLevelService service;

    public EmrLevelController(EmrLevelService service) {
        this.service = service;
    }

    /**
     * 保存电子病历评级目标和标准项能力映射。
     */
    @PutMapping("/targets")
    @PreAuthorize("@perm.has('evaluation.write')")
    public ResponseEntity<ApiResult<EmrLevelTargetResponse>> upsertTarget(
            @RequestBody @Valid EmrLevelTargetUpsertRequest request) {
        return ResponseEntity.ok(ApiResult.ok(service.upsertTarget(request)));
    }

    /**
     * 查询电子病历评级目标。
     */
    @GetMapping("/targets")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ResponseEntity<ApiResult<EmrLevelTargetResponse>> target(
            @RequestParam String hospitalOrgId,
            @RequestParam String standardVersion) {
        return ResponseEntity.ok(ApiResult.ok(service.target(hospitalOrgId, standardVersion)));
    }

    /**
     * 查询电子病历评级差距清单。
     */
    @GetMapping("/gaps")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ResponseEntity<ApiResult<List<EmrLevelGapResponse>>> gaps(
            @RequestParam String hospitalOrgId,
            @RequestParam String standardVersion) {
        return ResponseEntity.ok(ApiResult.ok(service.gaps(hospitalOrgId, standardVersion)));
    }

    /**
     * 查询电子病历评级进度。
     */
    @GetMapping("/progress")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ResponseEntity<ApiResult<EmrLevelProgressResponse>> progress(
            @RequestParam String hospitalOrgId,
            @RequestParam String standardVersion) {
        return ResponseEntity.ok(ApiResult.ok(service.progress(hospitalOrgId, standardVersion)));
    }
}
