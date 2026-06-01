# 用户与双模鉴权（方案A·MVP）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> 当前状态：历史实现计划，后续 AI 不得按本文继续施工；当前实现权威以 [AUTH-01](../../cards/D0/AUTH-01.md) 为准。本轮已收口为 V27 `platform_credential`、配置中心 `auth.mode` 和自定义 CSRF 双提交。

**Goal:** 打通一条真实的平台账号登录闭环（用户名/密码 → 后端签发 JWT → httpOnly cookie），让系统可登录、可按 13 角色体验权限，并修复登录页显示。

**Architecture:** 后端保持 OAuth2 Resource Server（验签不变），新增平台发证：`AuthController` 用 BCrypt 校验 `platform_credential` 后，用 Nimbus HS256（复用 `medkernel.jwt.dev-secret`）签发 JWT（claims `sub`/`tenant_id`/`roles`），写入 `mk_access` httpOnly+SameSite=Strict cookie；自定义 `CookieBearerTokenResolver` 让 Resource Server 从 cookie 读 JWT。前端去 token store，axios `withCredentials`，登录页真提交 + 主题色修复。

**CSRF 决策（当前实现）：** Spring 全局 CSRF 仍关闭，避免误伤 Bearer API / embed 客户端；浏览器 cookie 会话由 `CsrfDoubleSubmitFilter` 执行双提交校验，登录下发 `XSRF-TOKEN`，axios 自动提交 `X-XSRF-TOKEN`。

**Tech Stack:** Spring Boot 3 + Spring Security 6 OAuth2 Resource Server + Spring Data JDBC + Nimbus JOSE 9.37 + BCrypt + Flyway 五方言；React 18 + Antd 5 + axios + Vitest。

**Dev 注意（务必）：**
- `Secure` cookie 在 http://localhost **不会被浏览器存储/回传** → cookie 的 Secure 标志必须配置化（`medkernel.auth.cookie.secure`，dev 默认 `false`，prod `true`）。
- cookie **不设 Domain**（host-only），经 vite 代理（`/medkernel`→:18080）同源生效。
- JWT 验签依赖 `devJwtDecoder`（`@Profile({"dev","test"})`），本地/测试均激活；签发用同一 `medkernel.jwt.dev-secret`。

---

## 文件结构

**后端新增：**
- `engine/security/PlatformCredential.java` — 凭证实体（Record）
- `engine/security/PlatformCredentialRepository.java` — 仓库
- `engine/security/auth/JwtIssuer.java` — HS256 签发
- `engine/security/auth/AuthService.java` — 校验+签发+审计
- `engine/security/auth/AuthController.java` — /auth/login、/auth/logout
- `engine/security/auth/LoginRequest.java` / `LoginResponse.java` — Record DTO
- `shared/security/CookieBearerTokenResolver.java` — 从 cookie 读 JWT
- `shared/security/AuthCookieProperties.java` — cookie 配置（secure/ttl/name）
- `config/PlatformCredentialDevSeeder.java`（`engine/security/auth/` 下，`@Profile("dev")`）— 13 角色账号种子
- `resources/db/migration/{postgres,oracle,dm,kingbase,h2}/V27__platform_credential.sql` — 仅建表

**后端修改：**
- `shared/api/error/ErrorCode.java` — 追加 ENG-AUTH-001..003
- `shared/security/SecurityConfig.java` — permitAll `/auth/login`、`PasswordEncoder` Bean、`bearerTokenResolver` 接 cookie
- `medkernel-backend/src/main/resources/application.yml`（或 application-dev）— `medkernel.auth.*`

**前端修改：**
- `frontend/src/shared/api/client.ts` — `withCredentials: true`
- `frontend/src/pages/Login.tsx` — 真登录 + 登出
- `frontend/src/pages/Login.module.css` — 弃系统色走品牌 token
- `frontend/src/shared/api/hooks.ts` — 新增 `useLogin` / `useLogout`（封装 /auth）

**测试新增：**
- `engine/security/auth/JwtIssuerTest.java`
- `engine/security/auth/AuthControllerTest.java`
- `shared/security/CookieBearerTokenResolverTest.java`
- `frontend/src/pages/Login.test.tsx`

---

## Task 1: V27 五方言建表 platform_credential

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/postgres/V27__platform_credential.sql`
- Create: `.../oracle/V27__platform_credential.sql`
- Create: `.../dm/V27__platform_credential.sql`
- Create: `.../kingbase/V27__platform_credential.sql`
- Create: `.../h2/V27__platform_credential.sql`

- [ ] **Step 1: 写 postgres / kingbase（同语法）DDL**

`postgres/V27__platform_credential.sql` 与 `kingbase/V27__platform_credential.sql` 内容相同：

```sql
-- MedKernel v1.0 GA · V27 平台自建身份凭证表（外网 SaaS / 本地 dev 登录）
-- 仅建表；种子由 dev profile 的 PlatformCredentialDevSeeder 用 BCrypt 真哈希写入。
CREATE TABLE IF NOT EXISTS platform_credential (
    id              BIGSERIAL    PRIMARY KEY,
    credential_id   VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    username        VARCHAR(128) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    must_change_pwd CHAR(1)      NOT NULL DEFAULT 'Y',
    mfa_secret      VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id        VARCHAR(64),
    CONSTRAINT uk_platform_credential_id UNIQUE (credential_id),
    CONSTRAINT uk_platform_credential_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_platform_credential_status CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    CONSTRAINT ck_platform_credential_mustchg CHECK (must_change_pwd IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_platform_credential_login
    ON platform_credential (tenant_id, username, status);
```

- [ ] **Step 2: 写 h2 DDL**

`h2/V27__platform_credential.sql`（H2 用 `GENERATED ALWAYS AS IDENTITY`、`CURRENT_TIMESTAMP`）：

```sql
-- MedKernel v1.0 GA · V27 平台自建身份凭证表（H2 2.2）
CREATE TABLE IF NOT EXISTS platform_credential (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credential_id   VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    username        VARCHAR(128) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    must_change_pwd CHAR(1)      NOT NULL DEFAULT 'Y',
    mfa_secret      VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id        VARCHAR(64),
    CONSTRAINT uk_platform_credential_id UNIQUE (credential_id),
    CONSTRAINT uk_platform_credential_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_platform_credential_status CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    CONSTRAINT ck_platform_credential_mustchg CHECK (must_change_pwd IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_platform_credential_login
    ON platform_credential (tenant_id, username, status);
```

- [ ] **Step 3: 写 oracle DDL**

`oracle/V27__platform_credential.sql`（`NUMBER(19) GENERATED BY DEFAULT AS IDENTITY`、`VARCHAR2`、`TIMESTAMP WITH TIME ZONE`、`SYSTIMESTAMP`；Oracle 无 `IF NOT EXISTS`，索引/约束名 ≤30 字符）：

```sql
-- MedKernel v1.0 GA · V27 平台自建身份凭证表（Oracle 19c+）
CREATE TABLE platform_credential (
    id              NUMBER(19)    GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    credential_id   VARCHAR2(64)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    user_id         VARCHAR2(128) NOT NULL,
    username        VARCHAR2(128) NOT NULL,
    password_hash   VARCHAR2(100) NOT NULL,
    status          VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    must_change_pwd CHAR(1)       DEFAULT 'Y' NOT NULL,
    mfa_secret      VARCHAR2(128),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id        VARCHAR2(64),
    CONSTRAINT uk_plat_cred_id UNIQUE (credential_id),
    CONSTRAINT uk_plat_cred_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_plat_cred_status CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    CONSTRAINT ck_plat_cred_mustchg CHECK (must_change_pwd IN ('Y','N'))
);

CREATE INDEX idx_plat_cred_login ON platform_credential (tenant_id, username, status);
```

- [ ] **Step 4: 写 dm DDL**

`dm/V27__platform_credential.sql`（达梦：`NUMBER(19) IDENTITY`、`VARCHAR2`、`TIMESTAMP`、`CURRENT_TIMESTAMP`，约束名同 oracle 短名）：

```sql
-- MedKernel v1.0 GA · V27 平台自建身份凭证表（达梦 DM8）
CREATE TABLE platform_credential (
    id              NUMBER(19)    IDENTITY PRIMARY KEY,
    credential_id   VARCHAR2(64)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    user_id         VARCHAR2(128) NOT NULL,
    username        VARCHAR2(128) NOT NULL,
    password_hash   VARCHAR2(100) NOT NULL,
    status          VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    must_change_pwd CHAR(1)       DEFAULT 'Y' NOT NULL,
    mfa_secret      VARCHAR2(128),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by      VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id        VARCHAR2(64),
    CONSTRAINT uk_plat_cred_id UNIQUE (credential_id),
    CONSTRAINT uk_plat_cred_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_plat_cred_status CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    CONSTRAINT ck_plat_cred_mustchg CHECK (must_change_pwd IN ('Y','N'))
);

CREATE INDEX idx_plat_cred_login ON platform_credential (tenant_id, username, status);
```

- [ ] **Step 5: 运行 H2 迁移烟测验证 V27 生效**

Run: `cd medkernel-backend && mvn -q test -Dtest=H2BaselineMigrationTest`
Expected: PASS（Flyway 应用至 V27 无报错）。若 `MigrationBaselineContractTest` 断言迁移版本总数，需同步其期望值。

- [ ] **Step 6: Commit**

```bash
git add medkernel-backend/src/main/resources/db/migration/*/V27__platform_credential.sql
git commit -m "feat(auth): V27 五方言 platform_credential 建表"
```

---

## Task 2: PlatformCredential 实体 + 仓库

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/PlatformCredential.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/PlatformCredentialRepository.java`

- [ ] **Step 1: 写实体（Spring Data JDBC Record，对照 UserRoleAssignment 模式）**

```java
package com.medkernel.engine.security;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 平台自建身份凭证（外网 SaaS / 本地 dev）。真实身份认证内网走院方 IdP，本表仅用于平台签发。 */
@Table("platform_credential")
public record PlatformCredential(
    @Id Long id,
    @Column("credential_id") String credentialId,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("username") String username,
    @Column("password_hash") String passwordHash,
    @Column("status") String status,
    @Column("must_change_pwd") String mustChangePwd,
    @Column("mfa_secret") String mfaSecret,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
```

- [ ] **Step 2: 写仓库**

```java
package com.medkernel.engine.security;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformCredentialRepository extends ListCrudRepository<PlatformCredential, Long> {

    Optional<PlatformCredential> findByTenantIdAndUsername(String tenantId, String username);

    Optional<PlatformCredential> findByCredentialId(String credentialId);
}
```

- [ ] **Step 3: 编译**

Run: `cd medkernel-backend && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/PlatformCredential*.java
git commit -m "feat(auth): PlatformCredential 实体与仓库"
```

---

## Task 3: ErrorCode 追加 ENG-AUTH-001..003

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java:128`

- [ ] **Step 1: 把末条 ENG_KNOW_002 的结尾 `;` 改为 `,` 并追加三条**

将：
```java
    ENG_KNOW_002("ENG-KNOW-002", 409, "知识版本内容指纹冲突已存在", ErrorClass.DATA, false);
```
改为：
```java
    ENG_KNOW_002("ENG-KNOW-002", 409, "知识版本内容指纹冲突已存在", ErrorClass.DATA, false),
    ENG_AUTH_001("ENG-AUTH-001", 401, "用户名或密码不正确", ErrorClass.AUTH, false),
    ENG_AUTH_002("ENG-AUTH-002", 403, "账号已被禁用或锁定", ErrorClass.AUTH, false),
    ENG_AUTH_003("ENG-AUTH-003", 400, "登录请求参数不合法", ErrorClass.INPUT, false);
```

> 注意：`ENG_AUTH_001` 用 401 + 统一"用户名或密码不正确"文案，**不区分**用户是否存在（防枚举）。

- [ ] **Step 2: 编译**

Run: `cd medkernel-backend && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java
git commit -m "feat(auth): ErrorCode 追加 ENG-AUTH-001..003"
```

---

## Task 4: JwtIssuer（Nimbus HS256 签发）+ 测试

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/auth/JwtIssuer.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/security/auth/JwtIssuerTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtIssuerTest {

    private static final String SECRET = "medkernel-dev-secret-please-change-at-least-32-bytes";

    @Test
    void issuedTokenCarriesSubTenantRolesAndIsVerifiable() {
        JwtIssuer issuer = new JwtIssuer(SECRET, 28800);
        String token = issuer.issue("doctor-1", "t-1", List.of("doctor", "qa-manager"));

        JwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256")).build();
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("doctor-1");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("t-1");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("doctor", "qa-manager");
        assertThat(jwt.getExpiresAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=JwtIssuerTest`
Expected: 编译失败（JwtIssuer 不存在）。

- [ ] **Step 3: 写实现**

```java
package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.medkernel.shared.context.JwtClaimsResolver;

/** 平台 JWT 签发器（HS256，复用 medkernel.jwt.dev-secret，与 devJwtDecoder 对称验签）。 */
@Component
public class JwtIssuer {

    private final byte[] secret;
    private final long ttlSeconds;

    public JwtIssuer(
            @Value("${medkernel.jwt.dev-secret:medkernel-dev-secret-please-change-at-least-32-bytes}") String secret,
            @Value("${medkernel.auth.jwt.ttl-seconds:28800}") long ttlSeconds) {
        this.secret = secret.getBytes();
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String userId, String tenantId, List<String> roles) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(JwtClaimsResolver.CLAIM_TENANT_ID, tenantId)
                .claim(JwtClaimsResolver.CLAIM_ROLES, roles)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=JwtIssuerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/auth/JwtIssuer.java medkernel-backend/src/test/java/com/medkernel/engine/security/auth/JwtIssuerTest.java
git commit -m "feat(auth): JwtIssuer HS256 签发 + 测试"
```

---

## Task 5: cookie 配置属性 + PasswordEncoder Bean

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/security/AuthCookieProperties.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/security/SecurityConfig.java`（加 `PasswordEncoder` Bean）
- Modify: `medkernel-backend/src/main/resources/application.yml`（加 `medkernel.auth.*`，若无该文件则用 `application-dev.yml`）

- [ ] **Step 1: 写 cookie 配置属性**

```java
package com.medkernel.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 鉴权 cookie 配置。dev(http) 必须 secure=false，否则浏览器不存 cookie。 */
@ConfigurationProperties(prefix = "medkernel.auth.cookie")
public record AuthCookieProperties(
    String name,
    boolean secure,
    String sameSite,
    String path,
    long maxAgeSeconds
) {
    public AuthCookieProperties {
        if (name == null || name.isBlank()) name = "mk_access";
        if (sameSite == null || sameSite.isBlank()) sameSite = "Strict";
        if (path == null || path.isBlank()) path = "/medkernel";
        if (maxAgeSeconds <= 0) maxAgeSeconds = 28800;
    }
}
```

- [ ] **Step 2: SecurityConfig 加 PasswordEncoder + 启用 ConfigurationProperties**

在 `SecurityConfig` 类顶部注解追加 `@EnableConfigurationProperties(AuthCookieProperties.class)`，并加 Bean：

```java
    @org.springframework.context.annotation.Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
```
（import：`org.springframework.boot.context.properties.EnableConfigurationProperties`；类注解加 `@EnableConfigurationProperties(AuthCookieProperties.class)`。）

- [ ] **Step 3: application 配置加默认值**

在 `medkernel-backend/src/main/resources/application.yml` 增（dev profile 段，secure=false）：

```yaml
medkernel:
  auth:
    jwt:
      ttl-seconds: 28800
    cookie:
      name: mk_access
      secure: false      # 生产 profile 覆盖为 true
      same-site: Strict
      path: /medkernel
      max-age-seconds: 28800
```

- [ ] **Step 4: 编译**

Run: `cd medkernel-backend && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/shared/security/AuthCookieProperties.java medkernel-backend/src/main/java/com/medkernel/shared/security/SecurityConfig.java medkernel-backend/src/main/resources/application.yml
git commit -m "feat(auth): cookie 配置属性 + BCryptPasswordEncoder"
```

---

## Task 6: AuthService + AuthController（登录/登出，cookie + 审计）+ DTO + 测试

**Files:**
- Create: `engine/security/auth/LoginRequest.java`、`LoginResponse.java`
- Create: `engine/security/auth/AuthService.java`
- Create: `engine/security/auth/AuthController.java`
- Test: `engine/security/auth/AuthControllerTest.java`

- [ ] **Step 1: 写 DTO**

`LoginRequest.java`：
```java
package com.medkernel.engine.security.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    String tenantId
) {
    public String tenantOrDefault() {
        return (tenantId == null || tenantId.isBlank()) ? "t-1" : tenantId;
    }
}
```
`LoginResponse.java`：
```java
package com.medkernel.engine.security.auth;

import java.util.List;

public record LoginResponse(String userId, String tenantId, List<String> roles, boolean mustChangePwd) {}
```

- [ ] **Step 2: 写 AuthService（BCrypt 校验 + 签发 + 审计）**

```java
package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.IsolatedAuditPublisher;

/** 平台账号登录：BCrypt 校验 → 取角色 → 签发 JWT；成功/失败均隔离审计。 */
@Service
public class AuthService {

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final IsolatedAuditPublisher isolatedAudit;

    public AuthService(PlatformCredentialRepository credentials,
                       UserRoleAssignmentRepository roleAssignments,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer,
                       IsolatedAuditPublisher isolatedAudit) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.isolatedAudit = isolatedAudit;
    }

    /** 校验通过返回 [jwt, LoginResponse]；失败抛 ApiException 并发 FAILED 审计。 */
    public AuthResult login(String tenantId, String username, String rawPassword) {
        PlatformCredential cred = credentials.findByTenantIdAndUsername(tenantId, username).orElse(null);
        if (cred == null || !passwordEncoder.matches(rawPassword, cred.passwordHash())) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", username,
                ErrorCode.ENG_AUTH_001.code(), "登录失败：用户名或密码不正确 username=" + username));
            throw new ApiException(ErrorCode.ENG_AUTH_001);
        }
        if (!cred.active()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", cred.userId(),
                ErrorCode.ENG_AUTH_002.code(), "登录失败：账号禁用/锁定 status=" + cred.status()));
            throw new ApiException(ErrorCode.ENG_AUTH_002);
        }
        List<String> roles = roleAssignments
            .findActiveByTenantIdAndUserId(tenantId, cred.userId())
            .stream().map(UserRoleAssignment::roleCode).distinct().toList();
        String jwt = jwtIssuer.issue(cred.userId(), tenantId, roles);
        isolatedAudit.publishInNewTx(AuditEvent.of(
            AuditAction.LOGIN, "platform_credential", cred.userId(),
            "登录成功 username=" + username + " roles=" + roles));
        return new AuthResult(jwt,
            new LoginResponse(cred.userId(), tenantId, roles, "Y".equalsIgnoreCase(cred.mustChangePwd())));
    }

    public void logout(String userId) {
        isolatedAudit.publishInNewTx(AuditEvent.of(
            AuditAction.LOGOUT, "platform_credential", userId == null ? "anonymous" : userId, "登出"));
    }

    public record AuthResult(String jwt, LoginResponse response) {}
}
```

> 校验前先确认 `AuditEvent.of(action, resourceType, resourceId, summary)` 与 `AuditEvent.failure(action, resourceType, resourceId, errorCode, summary)` 工厂签名与既有调用一致（参 `ModelGatewayService` 用法）；若签名不同，按既有签名调整本处调用。

- [ ] **Step 3: 写 AuthController（cookie 注入/清除）**

```java
package com.medkernel.engine.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.security.AuthCookieProperties;

import jakarta.validation.Valid;

/** 平台账号登录/登出。/auth/login 在 SecurityConfig 中 permitAll。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieProperties cookieProps;

    public AuthController(AuthService authService, AuthCookieProperties cookieProps) {
        this.authService = authService;
        this.cookieProps = cookieProps;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthService.AuthResult result = authService.login(req.tenantOrDefault(), req.username(), req.password());
        ResponseCookie cookie = buildCookie(result.jwt(), cookieProps.maxAgeSeconds());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(ApiResult.ok(result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt == null ? null : jwt.getSubject());
        ResponseCookie cleared = buildCookie("", 0);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cleared.toString())
            .body(ApiResult.ok(null));
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from(cookieProps.name(), value)
            .httpOnly(true)
            .secure(cookieProps.secure())
            .sameSite(cookieProps.sameSite())
            .path(cookieProps.path())
            .maxAge(maxAge)
            .build();
    }
}
```

> 确认 `ApiResult.ok(...)` 存在且 `ApiResult.ok(null)` 合法（参既有 controller 用法，如 RecommendationEngineController）。

- [ ] **Step 4: 写 AuthController 测试（@SpringBootTest + MockMvc，test profile）**

```java
package com.medkernel.engine.security.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;

import java.time.Instant;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired WebApplicationContext ctx;
    @Autowired PlatformCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
        credentials.findByTenantIdAndUsername("t-1", "doctor-test").ifPresent(c -> credentials.deleteById(c.id()));
        credentials.save(new PlatformCredential(null, "cred-doctor-test", "t-1", "doctor-1",
            "doctor-test", encoder.encode("Mk@2026pw"), "ACTIVE", "N", null,
            Instant.now(), "test", Instant.now(), "test", "trace-test"));
    }

    @Test
    void login_success_setsHttpOnlyCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"doctor-test\",\"password\":\"Mk@2026pw\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("mk_access", true))
            .andExpect(cookie().exists("mk_access"))
            .andExpect(jsonPath("$.data.userId").value("doctor-1"));
    }

    @Test
    void login_wrongPassword_rejectedWithoutLeakingExistence() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"doctor-test\",\"password\":\"WRONG\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"nobody\",\"password\":\"WRONG\"}"))
            .andExpect(status().isUnauthorized());
    }
}
```

> `springSecurity()` MockMvc 配置确保过滤链生效。若项目已有 `@SpringBootTest` MockMvc 基类（参 `UserRoleAssignmentControllerTest`），复用其搭建方式。

- [ ] **Step 5: 运行测试**

Run: `cd medkernel-backend && mvn -q test -Dtest=AuthControllerTest`
Expected: PASS（两个用例）

- [ ] **Step 6: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/auth/*.java medkernel-backend/src/test/java/com/medkernel/engine/security/auth/AuthControllerTest.java
git commit -m "feat(auth): AuthService + AuthController 登录/登出 + cookie + 审计 + 测试"
```

---

## Task 7: CookieBearerTokenResolver + SecurityConfig 接入（cookie 读 JWT、permitAll /auth/login）

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/security/CookieBearerTokenResolver.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/shared/security/CookieBearerTokenResolverTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/security/SecurityConfig.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver =
        new CookieBearerTokenResolver(new AuthCookieProperties("mk_access", false, "Strict", "/medkernel", 28800));

    @Test
    void resolvesTokenFromCookie() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("mk_access", "jwt-from-cookie"));
        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-cookie");
    }

    @Test
    void fallsBackToAuthorizationHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer jwt-from-header");
        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-header");
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=CookieBearerTokenResolverTest`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 写实现（cookie 优先，回退 Authorization 头）**

```java
package com.medkernel.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/** 优先从 mk_access cookie 取 JWT；无则回退标准 Authorization: Bearer（兼容 embed / API 客户端）。 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final AuthCookieProperties cookieProps;
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    public CookieBearerTokenResolver(AuthCookieProperties cookieProps) {
        this.cookieProps = cookieProps;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (cookieProps.name().equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
```

- [ ] **Step 4: SecurityConfig 接入 cookie resolver + permitAll /auth/login**

修改 `filterChain`：构造函数注入 `CookieBearerTokenResolver cookieResolver`；`permitAll` 列表加 `"/api/v1/auth/login"`；`oauth2ResourceServer` 加 `.bearerTokenResolver(cookieResolver)`：

```java
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    TenantContextEnricherFilter tenantEnricher,
                                    CookieBearerTokenResolver cookieResolver) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // MVP：SameSite=Strict 主防御；CSRF 双提交 Phase 2
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/system/**",
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
                .jwt(jwt -> jwt.jwtAuthenticationConverter(buildJwtAuthenticationConverter())))
            .addFilterAfter(tenantEnricher, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
```

- [ ] **Step 5: 运行测试 + 编译**

Run: `cd medkernel-backend && mvn -q test -Dtest=CookieBearerTokenResolverTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/shared/security/CookieBearerTokenResolver.java medkernel-backend/src/test/java/com/medkernel/shared/security/CookieBearerTokenResolverTest.java medkernel-backend/src/main/java/com/medkernel/shared/security/SecurityConfig.java
git commit -m "feat(auth): CookieBearerTokenResolver + SecurityConfig 接入 cookie 读 JWT"
```

---

## Task 8: 13 角色账号 dev 种子（@Profile dev，BCrypt 真哈希）

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/auth/PlatformCredentialDevSeeder.java`

- [ ] **Step 1: 写种子器（dev profile，启动幂等 upsert 13 账号 + 缺失的角色分配）**

```java
package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

/**
 * 仅 dev profile：为 13 个角色各种一个可登录账号（username=角色码，默认密码 Mk@2026dev，须改密）。
 * 幂等：已存在则跳过。生产 profile 不加载本 Bean（无默认口令账号）。
 */
@Component
@Profile("dev")
public class PlatformCredentialDevSeeder implements ApplicationRunner {

    private static final String TENANT = "t-1";
    private static final String DEV_PASSWORD = "Mk@2026dev";
    // username -> {userId, roleCode}
    private static final Map<String, String[]> ACCOUNTS = Map.ofEntries(
        Map.entry("platform-admin", new String[]{"platform-admin-1", "platform-admin"}),
        Map.entry("group-admin", new String[]{"group-admin-1", "group-admin"}),
        Map.entry("hospital-admin", new String[]{"admin-1", "hospital-admin"}),
        Map.entry("it-ops", new String[]{"it-ops-1", "it-ops"}),
        Map.entry("medical-affairs", new String[]{"medical-affairs-1", "medical-affairs"}),
        Map.entry("qa-manager", new String[]{"qa-manager-1", "qa-manager"}),
        Map.entry("insurance-manager", new String[]{"insurance-manager-1", "insurance-manager"}),
        Map.entry("dept-head", new String[]{"dept-head-1", "dept-head"}),
        Map.entry("implementation", new String[]{"implementation-1", "implementation-engineer"}),
        Map.entry("specialist", new String[]{"specialist-1", "specialist"}),
        Map.entry("doctor", new String[]{"doctor-1", "doctor"}),
        Map.entry("nurse", new String[]{"nurse-1", "nurse"}),
        Map.entry("audit-compliance", new String[]{"audit-1", "audit-compliance"})
    );

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder encoder;

    public PlatformCredentialDevSeeder(PlatformCredentialRepository credentials,
                                       UserRoleAssignmentRepository roleAssignments,
                                       PasswordEncoder encoder) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.encoder = encoder;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        Instant now = Instant.now();
        ACCOUNTS.forEach((username, ur) -> {
            String userId = ur[0];
            String roleCode = ur[1];
            if (credentials.findByTenantIdAndUsername(TENANT, username).isEmpty()) {
                credentials.save(new PlatformCredential(null, "cred-" + userId, TENANT, userId, username,
                    encoder.encode(DEV_PASSWORD), "ACTIVE", "Y", null,
                    now, "dev-seeder", now, "dev-seeder", "seed"));
            }
            boolean hasRole = roleAssignments.findActiveByTenantIdAndUserId(TENANT, userId)
                .stream().anyMatch(a -> roleCode.equals(a.roleCode()));
            if (!hasRole) {
                roleAssignments.save(new UserRoleAssignment(null, TENANT, userId, roleCode,
                    "TENANT", TENANT, "Y", now, "dev-seeder", now, "dev-seeder"));
            }
        });
    }
}
```

- [ ] **Step 2: 编译**

Run: `cd medkernel-backend && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/auth/PlatformCredentialDevSeeder.java
git commit -m "feat(auth): dev profile 13 角色登录账号种子（BCrypt 真哈希）"
```

---

## Task 9: 前端 axios withCredentials

**Files:**
- Modify: `frontend/src/shared/api/client.ts:11-15`

- [ ] **Step 1: apiClient 加 withCredentials**

将 `axios.create({...})` 改为：
```ts
export const apiClient = axios.create({
  baseURL: "/medkernel/api/v1",
  timeout: 30_000,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});
```
（401 拦截器保持不变——已派发 `medkernel:auth-required`。）

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npm run typecheck`
Expected: 0 错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/shared/api/client.ts
git commit -m "feat(auth): axios withCredentials 携带 httpOnly cookie"
```

---

## Task 10: 前端 useLogin/useLogout hook

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`（文件末尾追加）

- [ ] **Step 1: 追加登录/登出 hook（对照文件内现有 hook 风格）**

在 `hooks.ts` 末尾追加：
```ts
export interface LoginPayload {
  username: string;
  password: string;
  tenantId?: string;
}

export interface LoginResult {
  userId: string;
  tenantId: string;
  roles: string[];
  mustChangePwd: boolean;
}

export function useLogin() {
  return useMutation({
    mutationFn: async (payload: LoginPayload) => {
      const resp = await apiClient.post<{ data: LoginResult }>("/auth/login", payload);
      return resp.data.data;
    },
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: async () => {
      await apiClient.post("/auth/logout");
    },
  });
}
```
（确认文件顶部已 `import { useMutation } from "@tanstack/react-query";` 与 `import { apiClient } from "./client";`；若已存在则不重复。）

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npm run typecheck`
Expected: 0 错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/shared/api/hooks.ts
git commit -m "feat(auth): 前端 useLogin/useLogout hook"
```

---

## Task 11: 前端 Login.tsx 真登录

**Files:**
- Modify: `frontend/src/pages/Login.tsx`

- [ ] **Step 1: 改 handleSubmit 为真实登录**

替换组件内逻辑：移除 `authNoticeVisible` 警告分支；引入 `useLogin` + `useNavigate` + `message`：
```tsx
import { Alert, Card, Form, Input, Button, Typography, Divider, message } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useLogin } from "@/shared/api/hooks";
import styles from "./Login.module.css";

const { Title, Text } = Typography;

export default function Login() {
  const [showSso, setShowSso] = useState(false);
  const navigate = useNavigate();
  const login = useLogin();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  async function handleSubmit(values: { username: string; password: string }) {
    setErrorMsg(null);
    try {
      await login.mutateAsync({ username: values.username, password: values.password });
      navigate("/dashboard");
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMsg(detail?.detail || detail?.message || "登录失败：用户名或密码不正确");
    }
  }
  // ...（保留原 JSX；把原 authNoticeVisible 的 Alert 替换为下面 errorMsg 版本）
```
JSX 中将原"真实身份认证尚未接入"Alert 改为：
```tsx
          {errorMsg && (
            <Alert type="error" showIcon message="登录失败" description={errorMsg} />
          )}
```
并给"进入工作台"按钮加 `loading={login.isPending}`。

- [ ] **Step 2: 类型检查 + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: 0 错误（注意路径别名 `@/` 是否可用；若否改相对路径 `../shared/api/hooks`）。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/Login.tsx
git commit -m "feat(auth): Login 真实提交 /auth/login + 错误态"
```

---

## Task 12: 登录页显示修复（弃系统色 → 品牌 token）

**Files:**
- Modify: `frontend/src/pages/Login.module.css`

- [ ] **Step 1: 在 .page 定义品牌色 CSS 变量并替换系统色**

将 `.page` 的 `color: CanvasText;` 与 `background: ...Canvas...` 段替换为固定品牌色（宪法 §8：医蓝 #1565c0），并在文件内把所有 `currentColor`/`Canvas`/`CanvasText` 引用改为这些变量：
```css
.page {
  /* 宪法 §8 设计 token，避免 Canvas/CanvasText 跟随 OS 深浅色导致失明 */
  --mk-text: #1f2329;
  --mk-text-muted: #5a6573;
  --mk-primary: #1565c0;
  --mk-surface: #ffffff;
  --mk-surface-tint: #f0f5ff;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 440px);
  align-items: center;
  gap: 32px;
  padding: 48px clamp(24px, 6vw, 72px);
  color: var(--mk-text);
  background: linear-gradient(135deg, var(--mk-surface-tint), var(--mk-surface));
}
```
- `.kicker` / `.brandTitle` → `color: var(--mk-primary);`
- `.primaryGoal` → `color: var(--mk-text);`
- `.signalItem` → `border: 1px solid rgba(21,101,192,0.16); background: #fff;`
- `.signalItem span` → `color: var(--mk-text-muted);`
- `.signalItem strong` → `color: var(--mk-text);`
- `.safetyCopy` → `color: var(--mk-text-muted);`
- `.loginCard` border/shadow 用 `rgba(21,101,192,0.12)` / `rgba(0,0,0,0.08)`（宪法 §8 阴影）。

（全文搜索 `Canvas`/`CanvasText`/`currentColor`，逐个替换为上述变量/固定色，确保无残留。）

- [ ] **Step 2: 确认无系统色残留**

Run: `cd frontend && grep -nE "Canvas|CanvasText|currentColor" src/pages/Login.module.css`
Expected: 无输出。

- [ ] **Step 3: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建成功。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Login.module.css
git commit -m "fix(auth): 登录页弃用系统色，走品牌 token 修复对比度"
```

---

## Task 13: 前端登录行为测试（非冒烟，针对 D2 缺口）

**Files:**
- Create: `frontend/src/pages/Login.test.tsx`

- [ ] **Step 1: 写测试（成功跳转 / 失败显错，mock useLogin）**

```tsx
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

const navigateMock = vi.fn();
const mutateAsyncMock = vi.fn();
vi.mock("react-router-dom", () => ({ useNavigate: () => navigateMock }));
vi.mock("@/shared/api/hooks", () => ({
  useLogin: () => ({ mutateAsync: mutateAsyncMock, isPending: false }),
}));

import Login from "./Login";

describe("Login", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateAsyncMock.mockReset();
  });

  it("登录成功跳转 /dashboard", async () => {
    mutateAsyncMock.mockResolvedValue({ userId: "doctor-1", tenantId: "t-1", roles: ["doctor"], mustChangePwd: false });
    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText("工号 / 账号"), { target: { value: "doctor" } });
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/dashboard"));
  });

  it("登录失败显示错误且不跳转", async () => {
    mutateAsyncMock.mockRejectedValue({ response: { data: { detail: "用户名或密码不正确" } } });
    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText("工号 / 账号"), { target: { value: "x" } });
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "y" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(screen.getByText("用户名或密码不正确")).toBeInTheDocument());
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
```
（若 `@/` 别名在测试环境不可用，改相对 import 并相应调整 `vi.mock` 路径。）

- [ ] **Step 2: 运行测试**

Run: `cd frontend && npx vitest run src/pages/Login.test.tsx`
Expected: 2 用例 PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/Login.test.tsx
git commit -m "test(auth): 登录成功跳转 / 失败显错 行为测试"
```

---

## Task 14: 全量验证 + 手动登录确认

- [ ] **Step 1: 后端全量测试**

Run: `cd medkernel-backend && mvn -q test`
Expected: 全绿（含新 JwtIssuerTest/AuthControllerTest/CookieBearerTokenResolverTest；既有测试不回归）。

- [ ] **Step 2: 前端 verify + build**

Run: `cd frontend && npm run verify && npm run build`
Expected: lint/format/typecheck/test 全绿 + 构建成功。

- [ ] **Step 3: 手动端到端确认（dev）**

Run: `./scripts/start-local.ps1`（或分别 `mvn spring-boot:run` + `npm run dev`），浏览器开 `http://localhost:5173`：
- 登录页左侧文字清晰可读（深/浅色环境）。
- 用 `doctor` / `Mk@2026dev` 登录 → 进入工作台，看到"临床运行"菜单。
- 用 `qa-manager` / `Mk@2026dev` 登录 → 看到"质控改进"菜单。
- 用 `platform-admin` / `Mk@2026dev` 登录 → 看到全量菜单。
- DevTools → Application → Cookies：`mk_access` 为 HttpOnly；Console 中 `document.cookie` 读不到它。
- 输错密码 → 显示"用户名或密码不正确"，不跳转。

- [ ] **Step 4: 最终提交（如有手动修正）+ 推送开 PR**

```bash
git push -u origin feat/user-auth-dual-mode
gh pr create --base main --title "feat(auth): 平台账号登录闭环（httpOnly cookie）+ 登录页修复" --body "见 docs/superpowers/specs/2026-05-29-user-auth-dual-mode-design.md 与 plans/2026-05-29-user-auth-dual-mode.md"
```

---

## 自检

**Spec 覆盖**：① 登录死胡同 → Task 6/7/11（真登录+cookie+permitAll）；② 显示 bug → Task 12；③ 双模架构 → Task 4/7（平台发证 + cookie 验签，内网 IdP 委托为 Phase 3 不在本计划）；④ 平台自建账号 → Task 1/2/6/8；⑤ 13 角色可登录验权限 → Task 8 + Task 14 手动确认；⑥ httpOnly cookie + 同源 → Task 5/6/9；⑦ 真实测试（非冒烟）→ Task 6/13。CSRF 双提交按架构决策列 Phase 2（已在头部与 Task 7 标注）。

**占位扫描**：无 TBD/TODO；每个代码步骤含完整代码与确切命令。

**类型一致**：`JwtIssuer.issue(userId, tenantId, roles)`、`AuthService.login(tenantId, username, rawPassword)→AuthResult{jwt,response}`、`PlatformCredential` 字段、cookie 名 `mk_access`、claim 名 `sub/tenant_id/roles` 前后一致。

**风险点（实现者注意）**：
- `AuditEvent.of/failure` 与 `ApiResult.ok` 的确切签名以现有代码为准（Task 6 已提示先核对）。
- `@/` 路径别名在前端测试/构建是否可用（Task 11/13 已给回退）。
- `MigrationBaselineContractTest` 若断言迁移数量需同步（Task 1 Step 5 已提示）。
- Oracle/达梦约束名 ≤30 字符（已用短名 `uk_plat_cred_*`）。
