package com.medkernel.shared.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.medkernel.shared.context.OrgLevel;

/**
 * MedKernel v1.0 GA · GA-ENG-BASE-02 数据范围声明。
 *
 * <p>标注在 Service / Controller 方法或类上，由 {@link DataScopeAspect} 在执行前验证
 * 当前 {@link com.medkernel.shared.context.RequestContext#currentOrgScope()} 是否满足要求。
 *
 * <p>不满足时抛 {@link com.medkernel.shared.api.error.ApiException}（{@code TENANT_CONTEXT_MISSING} /
 * {@code DATA_SCOPE_DENIED}），由 {@code GlobalExceptionHandler} 统一翻译为 ProblemDetail。
 *
 * <p>典型用法：
 * <pre>{@code
 * @RestController
 * @DataScope(requireTenant = true)
 * public class OrgUnitController { ... }
 *
 * @Service
 * @DataScope(requireTenant = true, requireAtLeast = OrgLevel.HOSPITAL)
 * public class HospitalScopedService { ... }
 * }</pre>
 *
 * <p>类级标注会被方法级标注覆盖（方法上的注解优先）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 是否要求当前 {@link com.medkernel.shared.context.OrgScope} 至少携带 {@code tenantId}。
     * 默认 true。
     */
    boolean requireTenant() default true;

    /**
     * 要求当前 {@link com.medkernel.shared.context.OrgScope} 至少携带某一组织树层级的 ID。
     * 例如设为 {@link OrgLevel#HOSPITAL} 则必须带 hospitalId。
     * 默认 {@link OrgLevel#TENANT}（等价于只要求 tenantId）。
     */
    OrgLevel requireAtLeast() default OrgLevel.TENANT;
}
