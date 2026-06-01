package com.medkernel.engine.experience;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 用户主题偏好控制器。
 */
@RestController
@RequestMapping("/api/v1/experience")
@DataScope(requireTenant = true)
public class ThemePreferenceController {

    private final UserPreferenceService service;

    public ThemePreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/theme-preference")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<ThemePreferenceResponse> getThemePreference() {
        return ApiResult.ok(service.getThemePreference());
    }

    @PutMapping("/theme-preference")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<ThemePreferenceResponse> saveThemePreference(@Valid @RequestBody ThemePreferenceRequest request) {
        return ApiResult.ok(service.saveThemePreference(request));
    }
}
