package com.medkernel.engine.context;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * 上下文字段目录 API（P2）。只读暴露从 canonical 标准资源派生的可用字段清单，
 * 供规则条件与路径守卫的字段选择器消费，解决「上下文没有字典 / 数据源可选」。
 */
@RestController
@RequestMapping("/api/v1/engine/context/field-catalog")
@DataScope(requireTenant = true)
public class ContextFieldCatalogController {

    private final ContextFieldCatalog catalog;

    public ContextFieldCatalogController(ContextFieldCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("@perm.has('context.read')")
    public ApiResult<List<ContextFieldDescriptor>> list(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(catalog.query(resourceType, keyword));
    }
}
