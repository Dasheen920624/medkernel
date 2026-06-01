package com.medkernel.shared.security;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.JwtClaimsResolver;
import com.medkernel.shared.context.TenantContextEnricherFilter;
import com.medkernel.shared.audit.persistence.AuditFallbackProperties;
import com.medkernel.shared.idempotency.IdempotencyFilter;
import com.medkernel.shared.idempotency.IdempotencyProperties;
import com.medkernel.shared.idempotency.IdempotencyRepository;

/**
 * GA-CORE-02 / W1-G3 闸门：Spring Security 6 OAuth2 Resource Server + JWT。
 *
 * <p>v1.0 GA 默认安全口径（与 docs/CONSTITUTION.md §1 第 8 条对齐）：
 * <ul>
 *   <li>JWT bearer token 由身份服务（OIDC / SAML / 国密 CA）签发；本应用作为 Resource Server 验签
 *   <li>无状态会话（不使用 HttpSession）
 *   <li>CSRF 关闭（前后端分离 + Bearer token）
 *   <li>白名单：/api/v1/system/** + /actuator/health + /actuator/prometheus + Swagger
 *   <li>dev profile 用 HS256 + 共享密钥简化本地开发；prod 必须用 RS256 + JWKS
 * </ul>
 *
 * <p>GA-ENG-BASE-01 升级：
 * <ul>
 *   <li>{@link #jwtAuthenticationConverter()} 把 JWT 的 {@code roles} claim 映射为 {@code ROLE_*} 权限</li>
 *   <li>在 {@link BearerTokenAuthenticationFilter} 之后追加 {@link TenantContextEnricherFilter}，
 *       让 Controller 内的代码可以通过 {@code RequestContext.currentOrgScope()} 拿到组织上下文</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({
    AuthCookieProperties.class,
    AuthJwtProperties.class,
    AuthSessionProperties.class,
    AuditFallbackProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    TenantContextEnricherFilter tenantEnricher,
                                    CookieBearerTokenResolver cookieResolver,
                                    IdempotencyRepository idempotencyRepository,
                                    IdempotencyProperties idempotencyProperties,
                                    ObjectMapper objectMapper) throws Exception {
        http
            // CSRF 关闭：前后端分离 + SameSite=Strict cookie，CSRF 双提交令牌方案列 Phase 2
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/system/**",
                    "/api/v1/bootstrap/init-token",
                    "/api/v1/bootstrap/password",
                    "/api/v1/auth/login",
                    "/api/v1/auth/logout",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(cookieResolver)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(buildJwtAuthenticationConverter()))
            )
            .addFilterAfter(tenantEnricher, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(
                new IdempotencyFilter(idempotencyRepository, idempotencyProperties, objectMapper),
                TenantContextEnricherFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT roles claim → ROLE_* 权限。
     *
     * <p>例：claim {@code roles=["doctor","qa-manager"]} → 权限 {@code ROLE_DOCTOR}、{@code ROLE_QA_MANAGER}。
     * 业务动作授权由 {@code @PreAuthorize("@perm.has(...)")} 使用有效权限画像统一判断。
     *
     * <p>不暴露为 Spring Bean —— 一旦作为 {@link Converter} bean 暴露，Spring MVC 的
     * {@code mvcConversionService} 会尝试将其注册为通用类型转换器，因泛型擦除丢失类型信息而启动失败。
     * 仅在 SecurityFilterChain 内部使用即可。
     */
    private JwtAuthenticationConverter buildJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> mapRoles(JwtClaimsResolver.resolveRoles(jwt)));
        return converter;
    }

    private Collection<GrantedAuthority> mapRoles(Collection<String> roles) {
        return roles.stream()
            .filter(r -> r != null && !r.isBlank())
            .map(r -> r.trim().toUpperCase().replace('-', '_'))
            .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * 本地 / 测试可使用 dev secret；生产必须显式配置 MEDKERNEL_AUTH_JWT_SECRET。
     */
    @Bean
    JwtDecoder jwtDecoder(JwtSecretResolver secretResolver,
                          SystemConfigService configService,
                          AuthSessionProperties sessionProperties) {
        String secret = secretResolver.resolve();
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            sessionPolicyValidator(configService, sessionProperties)
        ));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> sessionPolicyValidator(
            SystemConfigService configService,
            AuthSessionProperties sessionProperties) {
        return jwt -> {
            AuthSessionProperties policy = configService.runtimeSessionProperties(sessionProperties);
            Instant now = Instant.now();
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt != null && !issuedAt.plusSeconds(policy.idleTimeoutSeconds()).isAfter(now)) {
                return sessionExpired();
            }
            Instant sessionStartedAt = sessionStartedAt(jwt, issuedAt);
            if (sessionStartedAt != null && !sessionStartedAt.plusSeconds(policy.maxDurationSeconds()).isAfter(now)) {
                return sessionExpired();
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidatorResult sessionExpired() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
            "invalid_token",
            "会话已过期，请重新登录",
            null
        ));
    }

    private static Instant sessionStartedAt(Jwt jwt, Instant issuedAt) {
        Object claim = jwt.getClaims().get(AuthSessionClaims.SESSION_STARTED_AT);
        if (claim instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(text.trim()));
            } catch (NumberFormatException ignored) {
                return issuedAt;
            }
        }
        return issuedAt;
    }
}
