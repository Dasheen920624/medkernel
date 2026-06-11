package com.medkernel.shared.context;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 身份凭证 Claims 属性解析工具类（JWT Claims Resolver）。
 *
 * <p>用于从 JWT 令牌中解析多租户组织域范围、用户标识及分配的角色集合。
 *
 * <p>统一声明的属性字段命名规范（符合 OIDC 标准及平台扩展）：
 * <ul>
 *   <li>{@code sub} 表示用户唯一标识 userId（OIDC 标准）</li>
 *   <li>{@code tenant_id} 表示租户物理标识 tenantId（必填属性）</li>
 *   <li>{@code group_id} / {@code hospital_id} / {@code campus_id} / {@code site_id} / {@code department_id} / {@code ward_id} / {@code specialty_id} 表示多租户组织作用域的各层级标识</li>
 *   <li>{@code roles} 表示分配的角色编码列表</li>
 * </ul>
 *
 * <p>身份认证服务（OIDC / SAML / 国密 CA）签发 JWT 时必须按照本规则定义进行属性填充。
 * 支撑 GA-ENG-BASE-01 组织与租户上下文引擎的身份与属性提取。
 */
public final class JwtClaimsResolver {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_GROUP_ID = "group_id";
    public static final String CLAIM_HOSPITAL_ID = "hospital_id";
    public static final String CLAIM_CAMPUS_ID = "campus_id";
    public static final String CLAIM_SITE_ID = "site_id";
    public static final String CLAIM_DEPARTMENT_ID = "department_id";
    public static final String CLAIM_WARD_ID = "ward_id";
    public static final String CLAIM_SPECIALTY_ID = "specialty_id";
    public static final String CLAIM_ROLES = "roles";

    private JwtClaimsResolver() {
    }

    /**
     * 从 JWT 中解析组织上下文。所有字段都按需读取；缺失为 null。
     */
    public static OrgScope resolveOrgScope(Jwt jwt) {
        if (jwt == null) {
            return OrgScope.empty();
        }
        return new OrgScope(
            jwt.getClaimAsString(CLAIM_TENANT_ID),
            jwt.getClaimAsString(CLAIM_GROUP_ID),
            jwt.getClaimAsString(CLAIM_HOSPITAL_ID),
            jwt.getClaimAsString(CLAIM_CAMPUS_ID),
            jwt.getClaimAsString(CLAIM_SITE_ID),
            jwt.getClaimAsString(CLAIM_DEPARTMENT_ID),
            jwt.getClaimAsString(CLAIM_WARD_ID),
            jwt.getClaimAsString(CLAIM_SPECIALTY_ID)
        );
    }

    /**
     * 从 JWT 中解析 userId（OIDC {@code sub}）。
     */
    public static String resolveUserId(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    /**
     * 从 JWT 中解析角色列表。允许 {@code roles} claim 是 String[]、List&lt;String&gt; 或单个 String（逗号分隔）。
     */
    public static Collection<String> resolveRoles(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList();
        }
        Object raw = jwt.getClaim(CLAIM_ROLES);
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof Collection<?> coll) {
            return coll.stream()
                .filter(o -> o instanceof String)
                .map(o -> (String) o)
                .filter(s -> !s.isBlank())
                .toList();
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return Collections.emptyList();
            }
            return List.of(s.split("\\s*,\\s*"));
        }
        return Collections.emptyList();
    }
}
