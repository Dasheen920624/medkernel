package com.medkernel.engine.context;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 上下文字段目录 API（P2/P5）。只读暴露平台派生 + 租户自定义合并后的字段清单，供规则条件
 * 与路径守卫的字段选择器消费；并支持前台维护租户自定义字段（新增/删除），解决
 * 「上下文没有字典 / 数据源可选」且可前台维护。
 */
@RestController
@RequestMapping("/api/v1/engine/context/field-catalog")
@DataScope(requireTenant = true)
public class ContextFieldCatalogController {

    private final ContextFieldCatalogService service;

    public ContextFieldCatalogController(ContextFieldCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('context.read')")
    public ApiResult<List<ContextFieldDescriptor>> list(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String packageVersion) {
        return ApiResult.ok(service.query(resourceType, keyword, packageVersion));
    }

    @PostMapping
    @PreAuthorize("@perm.has('context.write')")
    public ApiResult<ContextFieldDescriptor> create(
            @RequestBody @Valid ContextFieldCatalogUpsertRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("@perm.has('context.write')")
    public ApiResult<ContextFieldDescriptor> update(
            @PathVariable String fieldId,
            @RequestBody @Valid ContextFieldCatalogUpsertRequest request) {
        return ApiResult.ok(service.update(fieldId, request));
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("@perm.has('context.write')")
    public ApiResult<Boolean> delete(@PathVariable String fieldId) {
        service.delete(fieldId);
        return ApiResult.ok(Boolean.TRUE);
    }
}
