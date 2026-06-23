package com.medkernel.engine.contract;

import java.util.List;

/**
 * MedKernel 服务契约声明。
 *
 * <p>本目录是 SYS-02 的契约索引：每个已暴露服务必须登记控制器、基础路径、
 * OpenAPI 路径、权限声明、审计点和明确公开的端点。
 *
 * @param id 稳定服务 ID
 * @param title 中文服务名称
 * @param controllerClassName 控制器类全名
 * @param basePath 控制器基础路径
 * @param openApiPaths OpenAPI 匹配路径
 * @param permissions 权限声明
 * @param auditPoints 审计点声明
 * @param publicEndpoints 允许匿名访问的端点键，格式为 {@code METHOD /path}
 */
public record ServiceContract(
    String id,
    String title,
    String controllerClassName,
    String basePath,
    List<String> openApiPaths,
    List<ServicePermissionDeclaration> permissions,
    List<ServiceAuditDeclaration> auditPoints,
    List<String> publicEndpoints
) {
    public ServiceContract {
        openApiPaths = List.copyOf(openApiPaths);
        permissions = List.copyOf(permissions);
        auditPoints = List.copyOf(auditPoints);
        publicEndpoints = List.copyOf(publicEndpoints);
    }

    public boolean declaresPermission(String code) {
        return permissions.stream().anyMatch(permission -> permission.code().equalsIgnoreCase(code));
    }

    public boolean isPublicEndpoint(String endpointKey) {
        return publicEndpoints.stream().anyMatch(endpoint -> endpoint.equalsIgnoreCase(endpointKey));
    }
}
