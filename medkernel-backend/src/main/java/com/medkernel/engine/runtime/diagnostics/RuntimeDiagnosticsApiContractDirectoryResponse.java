package com.medkernel.engine.runtime.diagnostics;

import java.util.List;

import com.medkernel.engine.security.PermissionDimension;
import com.medkernel.shared.audit.AuditAction;

/**
 * 运行诊断 API 契约目录响应。
 *
 * @param contracts 已脱敏服务契约清单
 */
public record RuntimeDiagnosticsApiContractDirectoryResponse(
    List<RuntimeDiagnosticsApiContractResponse> contracts
) {
    public RuntimeDiagnosticsApiContractDirectoryResponse {
        contracts = List.copyOf(contracts);
    }

    /**
     * 已脱敏服务契约。
     *
     * @param id 服务 ID
     * @param title 服务中文名称
     * @param basePath 服务基础路径
     * @param contractVersion 对外契约版本
     * @param openApiDocumentUrl OpenAPI 文档地址
     * @param fieldContractUrl 标准字段契约地址
     * @param openApiPaths OpenAPI 暴露路径
     * @param permissions 权限声明
     * @param auditPoints 审计声明
     * @param publicEndpoints 明确公开端点
     */
    public record RuntimeDiagnosticsApiContractResponse(
        String id,
        String title,
        String basePath,
        String contractVersion,
        String openApiDocumentUrl,
        String fieldContractUrl,
        List<String> openApiPaths,
        List<RuntimeDiagnosticsApiPermissionResponse> permissions,
        List<RuntimeDiagnosticsApiAuditResponse> auditPoints,
        List<String> publicEndpoints
    ) {
        public RuntimeDiagnosticsApiContractResponse {
            openApiPaths = List.copyOf(openApiPaths);
            permissions = List.copyOf(permissions);
            auditPoints = List.copyOf(auditPoints);
            publicEndpoints = List.copyOf(publicEndpoints);
        }
    }

    /**
     * API 权限声明。
     *
     * @param code 权限编码
     * @param dimension 权限分类
     * @param purpose 中文用途
     */
    public record RuntimeDiagnosticsApiPermissionResponse(
        String code,
        PermissionDimension dimension,
        String purpose
    ) {
    }

    /**
     * API 审计声明。
     *
     * @param action 审计动作
     * @param targetType 审计对象类型
     * @param purpose 中文说明
     */
    public record RuntimeDiagnosticsApiAuditResponse(
        AuditAction action,
        String targetType,
        String purpose
    ) {
    }
}
