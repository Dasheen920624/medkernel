package com.medkernel.engine.experience;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 页面保存视图控制器。
 */
@RestController
@RequestMapping("/api/v1/experience")
@DataScope(requireTenant = true)
public class SavedViewController {

    private final SavedViewService service;

    public SavedViewController(SavedViewService service) {
        this.service = service;
    }

    @GetMapping("/saved-views")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SavedViewResponse>> list(
        @RequestParam("pageKey")
        @NotBlank(message = "页面标识不能为空")
        @Size(max = 96, message = "页面标识长度超限")
        String pageKey
    ) {
        return ApiResult.ok(service.list(pageKey));
    }

    @PutMapping("/saved-views")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SavedViewResponse> upsert(@Valid @RequestBody SavedViewRequest request) {
        return ApiResult.ok(service.upsert(request));
    }
}
