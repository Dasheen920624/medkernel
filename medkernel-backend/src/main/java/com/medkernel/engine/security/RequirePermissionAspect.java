package com.medkernel.engine.security;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.PermissionDeniedException;

/**
 * MedKernel v1.0 GA · {@link RequirePermission} 切面。
 */
@Aspect
@Component
public class RequirePermissionAspect {

    private final PermissionEvaluator permissionEvaluator;

    public RequirePermissionAspect(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Before("@annotation(com.medkernel.engine.security.RequirePermission) " +
            "|| @within(com.medkernel.engine.security.RequirePermission)")
    public void enforce(JoinPoint joinPoint) {
        RequirePermission annotation = resolveAnnotation(joinPoint);
        if (annotation != null) {
            enforce(annotation);
        }
    }

    /**
     * 执行单个权限声明的校验，便于单元测试和非 AOP 场景复用。
     */
    public void enforce(RequirePermission requirePermission) {
        if (requirePermission == null) {
            return;
        }
        PermissionCode permission = requirePermission.value();
        if (!permissionEvaluator.has(permission)) {
            throw new PermissionDeniedException(
                permission.code(),
                permission.displayName(),
                "/security/request-access");
        }
    }

    private RequirePermission resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission methodAnn = AnnotationUtils.findAnnotation(method, RequirePermission.class);
        if (methodAnn != null) {
            return methodAnn;
        }
        Class<?> targetClass = joinPoint.getTarget() == null
            ? method.getDeclaringClass()
            : joinPoint.getTarget().getClass();
        return AnnotationUtils.findAnnotation(targetClass, RequirePermission.class);
    }
}
