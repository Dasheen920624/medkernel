package com.medkernel.engine.contract;

import com.medkernel.engine.security.PermissionDimension;

/**
 * 服务契约中的权限声明。
 *
 * @param code 权限编码，必须能被 {@code PermissionCode.fromCode} 解析
 * @param dimension 权限分类
 * @param purpose 权限在该服务中的业务用途
 */
public record ServicePermissionDeclaration(
    String code,
    PermissionDimension dimension,
    String purpose
) {}
