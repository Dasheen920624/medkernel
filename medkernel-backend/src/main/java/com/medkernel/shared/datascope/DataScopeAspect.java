package com.medkernel.shared.datascope;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * MedKernel v1.0 GA · {@link DataScope} 切面。
 *
 * <p>在方法执行前验证 {@link RequestContext#currentOrgScope()} 满足 {@link DataScope} 声明的最低范围。
 *
 * <p>未通过时抛 {@link ApiException}，由 {@code GlobalExceptionHandler} 翻译为 401/403 ApiResult。
 *
 * <p>注解发现顺序：先看方法级注解，方法级不存在则看类级注解；都不存在则切面跳过。
 */
@Aspect
@Component
public class DataScopeAspect {

    @Before("@annotation(com.medkernel.shared.datascope.DataScope) " +
            "|| @within(com.medkernel.shared.datascope.DataScope)")
    public void enforce(JoinPoint joinPoint) {
        DataScope annotation = resolveAnnotation(joinPoint);
        if (annotation == null) {
            return;
        }

        OrgScope scope = RequestContext.currentOrgScope();
        if (annotation.requireTenant() && (scope == null || !scope.hasTenant())) {
            throw ApiException.tenantMissing();
        }
        // requireAtLeast=TENANT 是默认值，等价于"只要求租户"，由上面的 requireTenant 校验覆盖；
        // 仅当要求严于 TENANT（即 GROUP/HOSPITAL/CAMPUS/SITE/DEPARTMENT）时才追加组织树校验。
        if (annotation.requireAtLeast() != OrgLevel.TENANT
                && !satisfiesAtLeast(scope, annotation.requireAtLeast())) {
            throw new ApiException(ErrorCode.DATA_SCOPE_DENIED,
                "当前组织上下文未达到要求的最低层级 " + annotation.requireAtLeast().name());
        }
    }

    private DataScope resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DataScope methodAnn = AnnotationUtils.findAnnotation(method, DataScope.class);
        if (methodAnn != null) {
            return methodAnn;
        }
        Class<?> targetClass = joinPoint.getTarget() == null
            ? method.getDeclaringClass()
            : joinPoint.getTarget().getClass();
        return AnnotationUtils.findAnnotation(targetClass, DataScope.class);
    }

    private boolean satisfiesAtLeast(OrgScope scope, OrgLevel level) {
        if (scope == null) {
            return level == null;
        }
        return switch (level) {
            // 平台权威层尚未接入请求作用域（属 P0-1.3 平台空间约定）；普通租户作用域不具备平台权威，按 fail-closed 拒绝
            case PLATFORM -> false;
            case TENANT -> scope.tenantId() != null && !scope.tenantId().isBlank();
            case GROUP -> notBlank(scope.groupId());
            case HOSPITAL -> notBlank(scope.hospitalId());
            case CAMPUS -> notBlank(scope.campusId());
            case SITE -> notBlank(scope.siteId());
            case DEPARTMENT -> notBlank(scope.departmentId());
        };
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
