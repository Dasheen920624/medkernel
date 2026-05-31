package com.medkernel.engine.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MedKernel v1.0 GA · 方法级权限声明。
 *
 * <p>标注在 Service / Controller 方法或类上，由 {@link RequirePermissionAspect} 统一调用
 * {@link PermissionEvaluator} 判定，避免业务代码直接读取角色或绕过租户权限覆盖。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 需要具备的权限码。 */
    PermissionCode value();
}
