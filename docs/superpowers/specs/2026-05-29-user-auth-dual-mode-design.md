# 用户与双模鉴权设计（方案 A · 分期）

> 日期：2026-05-29 · 作者：Claude（与用户 brainstorming 定稿）
> 状态：历史设计材料，当前实现权威以 [AUTH-01](../../cards/D0/AUTH-01.md) 为准；本轮已收口为 V27 `platform_credential`、配置中心 `auth.mode` 和自定义 CSRF 双提交。
> 触发：PR #139「收紧登录入口」后系统 100% 锁死（无任何发证途径）；登录页显示失明；内外网身份/注册/角色功能需重新梳理。

---

## 1. 背景与问题

当前系统**连登录都进不去**，根因三重叠加：

1. **前端登录是死胡同**：`Login.tsx` 的 `handleSubmit` 只弹"真实身份认证尚未接入"警告、不跳转；`router.tsx` 把 `/` 指向 `/login`；`AppLayout` 要求 `/me` 安全画像才放行。
2. **全系统无发证能力**：后端是纯 OAuth2 Resource Server（`SecurityConfig` 只验签 JWT、不签发），未接院方 IdP，无 dev 登录；`client.ts` 的 axios 从未附 token。
3. **种子账号无密码**：V25 只种 `user_role_assignment`（9 账号），明确"真实身份认证由院方统一身份源提供"。

**登录页显示问题**：`Login.module.css` 用 CSS 系统色 `Canvas/CanvasText/currentColor`，跟随 OS 深浅色而非 App 主题，导致左侧文字在某些环境近乎不可见（违反宪法 §8 颜色须走 token）。

**设计缺口**：内网委托 IdP 已设计未接；**外网 SaaS 无任何注册/登录设计**；13 个角色无法登录体验。

## 2. 目标与范围

**目标**：建立内外网双模身份体系，让系统可真实登录、可按角色体验权限，并修复登录页显示。

**决策（已与用户确认）**：
- 身份模型：**双模** —— 内网委托院方 IdP（OIDC/CAS/SAML/国密CA）；外网 SaaS **平台自建账号**（用户名/密码 → 平台签发 JWT）；两者经**同一 Resource Server 验签**。
- 注册模型：**平台开通租户 + 邀请制**（无公开自助注册，符合医疗合规与租户隔离）。
- 路径：**方案 A 分期**——本轮先打通一条**真实**的平台账号登录闭环（解锁 + 验角色），其余排后续。
- Token 载体：**httpOnly cookie**（XSS 不可窃取）+ CSRF 双提交防护。

**非目标（本轮不做，列入后续）**：MFA、国密 SM3/SM2 口令、失败锁定/限流、租户开通向导 UI、邀请制 UI、内网 IdP 委托接线。

## 3. 架构：双模、同一验签内核

```
内网（Phase 3）  院方 IdP（OIDC/CAS/SAML/国密CA）─┐
                                                  ├─► JWT（claims: sub, tenant_id, roles[]）
外网/本轮        平台 AuthController /auth/login ──┘        │
                （BCrypt 校验 platform_credential）         ▼
                                              同一 OAuth2 Resource Server 验签
                                              （JwtClaimsResolver: roles → ROLE_*）
                                                            │
                                                            ▼
                                              @DataScope + @perm.has 权限/数据范围
```

- 后端 Resource Server **验签逻辑不变**；新增"平台发证"来源。
- JWT 签发：本轮用现有 `medkernel.jwt.dev-secret`（HS256，与 `devJwtDecoder` 对称）；生产换非对称密钥/真实 IdP。
- 配置开关 `medkernel.auth.mode = PLATFORM | DELEGATED | BOTH`，默认 `PLATFORM`（本轮）；内网部署切 `DELEGATED`/`BOTH`。两条来源解耦，互不影响验签。

## 4. Token 载体：httpOnly cookie + CSRF

- **登录成功**：后端 `Set-Cookie: mk_access=<JWT>; HttpOnly; Secure; SameSite=Strict; Path=/medkernel; Max-Age=<expSeconds>`。JS 读不到，规避 XSS 窃取，也满足 BASE-10「禁止 token 写 localStorage」。
- **请求携带**：浏览器自动带 cookie；前端 `apiClient` 设 `withCredentials: true`，不再手工附 `Authorization`。
- **Resource Server 读 cookie**：自定义 `CookieBearerTokenResolver` —— 优先从 `mk_access` cookie 取 JWT，回退 `Authorization: Bearer`（兼容 embed launch / 纯 API 客户端），接入 `oauth2ResourceServer(... .bearerTokenResolver(...))`。
- **CSRF 防护**（cookie 鉴权必须）：当前实现采用自定义 `CsrfDoubleSubmitFilter`，仅保护带 `mk_access` cookie 的写操作；登录成功下发非 httpOnly `XSRF-TOKEN`，前端 axios 自动把其值放入 `X-XSRF-TOKEN` 头随写操作提交。Bearer API / embed 客户端不受 CSRF 过滤影响，避免全局 Spring CSRF 误伤非浏览器调用。
- **登出**：`POST /auth/logout` → `Set-Cookie mk_access=; Max-Age=0` 清除 + 留痕。
- **401 处理**：`client.ts` 拦截 401 → 派发 `medkernel:auth-required` → 跳 `/login`（无客户端 token 可清，cookie 由服务端/过期失效）。
- 同源性：dev 下 vite 代理 `/medkernel`→`:18080`，浏览器视为同源，cookie 直接生效；生产前端与 API 同源（或经网关同域）部署。

## 5. 数据模型与开通流程

**复用**：`org_unit`（租户/组织）、`user_role_assignment`（V25 已种 9 账号）、`@DataScope` 数据范围、13 个 `RoleCode`（平台/集团/医院管理员、信息科、医务处、质控办、医保办、科主任、专科专家、医生、护理、合规审计、实施工程师）。

**新增表 `platform_credential`（五方言迁移 V27）**：

| 列 | 类型 | 说明 |
|---|---|---|
| id | 主键（方言自增/序列）| |
| credential_id | VARCHAR(64) | 业务 ID |
| tenant_id | VARCHAR(64) | 租户隔离 |
| user_id | VARCHAR(64) | 关联 user_role_assignment.user_id |
| username | VARCHAR(128) | 登录名（工号/账号），租户内唯一 |
| password_hash | VARCHAR(100) | BCrypt |
| status | VARCHAR(16) | ACTIVE / DISABLED / LOCKED |
| must_change_pwd | CHAR(1) | 首登改密标志 |
| mfa_secret | VARCHAR(128) NULL | 预留（Phase 2）|
| + 审计字段 | | created_at/by, updated_at/by, trace_id |

唯一约束 `(tenant_id, username)`；避免保留字（不用 USER/PASSWORD 裸列名）。

**开通链（设计，本轮仅种子 + 基础建号）**：平台管理员建租户 + 首个租户管理员 → 租户管理员邀请/开通成员并授角色。本轮用 V27 种子 + 复用 `UserRoleAssignmentController` + 新增建凭证端点；向导/邀请 UI 进 Phase 2。

## 6. 本轮 MVP 交付清单

### 后端
1. `platform_credential` 表 + **V27 五方言迁移**；**种子**：为 13 个角色各建 1 个可登录账号（如 `doctor` / `qa-manager` / `platform-admin`…），默认密码（统一 dev 密码，`must_change_pwd=Y`），关联 `user_role_assignment`（补齐 V25 未覆盖的 doctor/nurse/specialist/audit-compliance）。
2. `AuthController`：
   - `POST /api/v1/auth/login`（Record DTO：username, password）→ BCrypt 校验 → 签发 JWT（claims sub/tenant_id/roles）→ Set-Cookie httpOnly。
   - `POST /api/v1/auth/logout` → 清 cookie + 审计。
   - 失败（用户不存在/密码错/禁用）→ 统一 `ENG-AUTH-00x` + ProblemDetail，**不泄露**"用户是否存在"。
   - 登录成功/失败均经 `IsolatedAuditPublisher` 留痕（outcome=SUCCESS/FAILED, action=LOGIN）。
3. `JwtIssuer`（签发）+ `CookieBearerTokenResolver`（读 cookie）+ `SecurityConfig` 接入：`/auth/login`、`/actuator/health`、`/system/ping`、swagger permitAll；CSRF 启用（GET 豁免）；`bearerTokenResolver` 用 cookie 优先。
4. `PasswordEncoder`（BCrypt）Bean。

### 前端
5. `Login.tsx`：真提交 → `POST /auth/login` → 200 跳 `/dashboard`（cookie 已种）；失败显示真实错误（去掉假"未接入"提示）。提供"退出登录"。
6. `client.ts`：`withCredentials: true` + 自动附 `X-XSRF-TOKEN`；401 → 跳 `/login`。
7. **修显示**：`Login.module.css` 弃用 `Canvas/CanvasText/currentColor`，改 Antd 主题 token（`colorPrimary` 医蓝 `#1565c0`、`colorText` 等）或 CSS 变量，确保左侧 hero 文字在深浅色环境均高对比。
8. 角色权限体验：登录后 `/me` 已驱动菜单/权限/数据范围——**不同种子账号登录看到不同菜单**（医生→临床运行、质控办→质控改进、平台管理员→全量），即"院内账号权限怎么用"。

### 测试（针对 D2「前端仅冒烟」缺口，写真实行为测试）
9. 后端：登录成功签发 cookie + claims；密码错/禁用/不存在统一拒绝且不泄露；登录审计 SUCCESS/FAILED；CookieBearerTokenResolver 从 cookie 解析；登出清 cookie。
10. 前端：登录提交 → 跳转 dashboard；401 → 跳 login；登录页主题色渲染（断言非系统色）。

## 7. 安全与合规

- 本轮：BCrypt 口令、httpOnly+Secure+SameSite cookie、CSRF 双提交、登录审计、错误不泄露用户存在性。
- 后续：MFA（mfa_secret 已预留）、国密 SM3 口令/SM2、失败锁定+限流、租户开通向导、邀请制、内网 IdP 委托。
- 宪法对齐：§1#1 国情合规（国密 Phase 2 落，本轮 BCrypt 为安全基线）、§8 颜色 token、BASE-10 禁 localStorage token（cookie 满足）、审计留痕。

## 8. 分期

| 阶段 | 内容 |
|---|---|
| **本轮 MVP** | §6 全部（平台账号登录 + httpOnly cookie + CSRF + 13 角色种子 + 显示修复 + 真实测试）|
| Phase 2 | 租户开通向导 + 邀请制成员管理 UI + MFA + 国密 SM3/SM2 + 失败锁定/限流 |
| Phase 3 | 内网 IdP 委托（OIDC/CAS/SAML/国密CA）+ `auth.mode` 切换 + 非对称签名密钥 |

## 9. 风险与权衡

- **CSRF**：cookie 鉴权引入 CSRF 面，已用 SameSite=Strict + 双提交 token 缓解；embed iframe 走独立 launch token（不依赖主 cookie），不受 SameSite 影响。
- **HS256 对称密钥**：本轮签发/验签共用 dev-secret，仅适合单体/同信任域；生产须换非对称（Phase 3 随 IdP 委托一并升级）。
- **默认密码种子**：仅 dev/演示便利，`must_change_pwd=Y` 强制首登改密；生产环境迁移须用一次性强随机口令或禁用种子。

## 10. 验收标准

- 用任一种子账号可登录进入工作台；不同角色看到不同菜单与数据范围。
- 登录页左右两侧文字在深/浅色环境均清晰可读。
- token 仅存 httpOnly cookie（DevTools 中 JS 读不到）；写操作带 CSRF 头。
- 登录成功/失败有 audit_event；密码错不泄露用户是否存在。
- 后端 + 前端新增测试全绿；`npm run verify`/`build` 与后端 `mvn test` 通过；五方言 V27 迁移烟测通过。
