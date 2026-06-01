# AUTH-01 · 双模身份与登录闭环

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：用户与双模鉴权设计 [2026-05-29-user-auth-dual-mode-design](../../superpowers/specs/2026-05-29-user-auth-dual-mode-design.md) §3–§7 · 落地规划 §5.1 用户与身份 · 宪法 §1 #1 国情合规 / §8。
> 收口状态：本轮按卡把 #146 MVP 收进当前权威口径，补齐 `auth.mode` 配置中心、CSRF 双提交、委托登录诚实挂点、错误不泄露与审计证据；真实院方 IdP/JWKS/国密证书链登记为 `DEFER-005`，不得伪造已接入。

## 身份
- 卡 ID：AUTH-01（backlog v8.2 D0 新增）
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规
- 依赖卡：[BASE-01](BASE-01.md)（租户上下文）· [BASE-02](BASE-02.md)（角色→JWT claims）· [BASE-03](BASE-03.md)（ProblemDetail）· [BASE-04](BASE-04.md)（登录审计）· [CONFIG-01](CONFIG-01.md)（auth.mode 配置外置）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer，高风险建议双签）

## 目标

交付**双模身份与登录闭环**（后端 authN 内核）：内网委托院方 IdP（OIDC/CAS/SAML/国密CA）+ 外网平台自建账号（用户名/密码→签发 JWT），经**同一 Resource Server 验签**；token 仅 httpOnly cookie + CSRF；登录成功/失败全审计、错误不泄露用户存在性。

## 功能要求（原子可测条目）

- [x] **FR-1 双模架构**：`auth.mode = PLATFORM / DELEGATED / BOTH`（经 [CONFIG-01](CONFIG-01.md) 配置外置，**禁写死 yml**）；平台账号与委托 IdP 两条来源解耦，同一 Resource Server 验签。当前真实 IdP 未接入时只返回 `NOT_CONNECTED` / `ENG-AUTH-014`，不签发假 JWT。
- [x] **FR-2 平台登录闭环**：`POST /api/v1/auth/login`（Record DTO：username/password/tenantId）→ BCrypt 校验 → 签发 JWT（claims sub/tenant_id/roles）→ `Set-Cookie mk_access httpOnly+Secure+SameSite=Strict`。
- [x] **FR-3 httpOnly + CSRF**：token **仅** httpOnly cookie（禁 localStorage，JS 读不到，呼应 [BASE-10](BASE-10.md)）；CSRF 双提交（`XSRF-TOKEN` + `X-XSRF-TOKEN`）；`CookieBearerTokenResolver` cookie 优先、回退 `Authorization: Bearer`（兼容 embed/API）。
- [x] **FR-4 错误不泄露**：用户不存在/密码错/禁用 → 统一 `ENG-AUTH-001` + ProblemDetail，**不泄露"用户是否存在"**（核心 §8）。
- [x] **FR-5 登录审计**：登录成功/失败均经 [BASE-04](BASE-04.md) 留痕（outcome=SUCCESS/FAILED, action=LOGIN, 不存密码明文）。
- [x] **FR-6 凭证表 + MFA 机制 + 生产密钥**：`platform_credential` 表（五方言文件，当前真实运行验收 PostgreSQL + Oracle）+ 13 角色 dev 可登录账号（关联 user_role_assignment）；**MFA 机制**（mfa_secret 绑定/校验，供 [BASE-11](BASE-11.md)/[SUPERADMIN-01](SUPERADMIN-01.md)/[AUTH-03](AUTH-03.md) 强制启用）；生产禁止 dev 默认 JWT 密钥，真实院方 IdP/JWKS/国密证书链关闭证据登记在 `DEFER-005`。

## 接口契约 / 页面契约
### 接口契约
- 端点：`POST /api/v1/auth/login` · `POST /api/v1/auth/logout`（清 cookie + 审计）· `GET /api/v1/auth/delegated/status` · `POST /api/v1/auth/delegated/callback`（DELEGATED/BOTH 下无真实 IdP 时诚实 `NOT_CONNECTED`）。
- DTO：`LoginRequest`（username/password/tenantId）Record + Bean Validation；JWT claims 结构。
- 响应信封：成功 Set-Cookie + `ApiResult`；失败 `ProblemDetail`（`ENG-AUTH-00x`，中文，不泄露存在性）。
- 状态机：N·A —— 登录是认证动作，凭证 status（ACTIVE/DISABLED/LOCKED）非四资产类。
- 幂等 / 错误码 / traceId：登录非幂等（每次签发）；`ENG-AUTH-001` 统一认证失败，`ENG-AUTH-013` 表示当前认证模式不允许平台登录，`ENG-AUTH-014` 表示委托 IdP 未接入；全程 traceId（[OBS-01](OBS-01.md)）。

### 页面契约
N·A —— 登录页 UI 是 [AUTH-02](AUTH-02.md)；本卡是 authN 后端内核。

## 数据与迁移
- 表族：`platform_credential`（credential_id/tenant_id/user_id/username/password_hash(BCrypt)/status/must_change_pwd/mfa_secret + 审计字段）。
- 主键：方言自增/序列；唯一约束：`(tenant_id, username)`；避免保留字（不用 USER/PASSWORD 裸列名）。
- 安全：password_hash BCrypt（国密 SM3 在 [AUTH-03](AUTH-03.md)）；mfa_secret 加密存储；不存明文。
- 5 方言迁移：V27 等 h2/postgres/oracle/dm/kingbase + 中文注释；当前真实运行范围验证 H2 / PostgreSQL 15 / Oracle 21 到 V44，达梦 / 人大金仓真实环境最终适配登记 `DEFER-001`。
- 种子：`PlatformCredentialDevSeeder` 仅 `dev` profile 生成 13 角色与内置超管可登录账号，生产首发身份走 [BASE-11](BASE-11.md) init token 与强制改密 / MFA。

## 视角清单（11 视角逐条）
1. **产品架构**：登录是系统唯一入口；双模同一验签内核，平台/内网两来源不分叉。
2. **产品体验**：N·A —— 登录页体验在 [AUTH-02](AUTH-02.md)；本卡供其后端。
3. **系统与数据架构**：JWT 签发/验签 + cookie 解析 + CSRF；登录高并发；凭证表租户唯一。
4. **临床医疗安全**：N·A —— 但"谁登入"是所有临床操作可追责的起点（医师确认链的根）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：★本卡主战场 —— BCrypt + httpOnly+Secure+SameSite + CSRF + 错误不泄露存在性 + 登录审计（等保/个保法，核心 §8）；国密/MFA 由 [AUTH-03](AUTH-03.md) 强化。
7. **集团化与多租户治理**：登录带 tenantId，JWT claims 含 tenant_id（[BASE-01](BASE-01.md) 上下文起点）；跨租户账号隔离。
8. **集成与互操作**：★内网委托院方 IdP（OIDC/CAS/SAML/国密CA）经统一链路（核心 §10）；embed/API 走 Bearer 回退。
9. **运维 / SRE / 国产化**：auth.mode 切换内外网形态；生产非对称密钥；国产化 IdP（国密CA）。
10. **质量与真实性审计**：★禁假登录（#139 后的"未接入"死胡同已修）；真实 BCrypt 校验 + 真实审计（核心 #18）。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0（纯确定性认证）。

## 适用不变量
- 命中核心约束：**#1 国情合规（国密/数据本地）** · **#8 敏感字段（口令 BCrypt/不泄露）** · **#19 auth.mode 配置外置** · **§8 审计 + §10 IdP 互操作**。
- 本卡落点：双模同一验签内核 + httpOnly cookie + CSRF + 登录审计 + 凭证表，把"系统可真实、安全地登入"做成事实，并为 MFA/国密/IdP 预留挂点。

## 验收 + 验证
- [x] **AC-1（FR-2/3）**：种子账号登录 → 签发 JWT + Set-Cookie httpOnly（DevTools 中 JS 读不到 token）；写操作带 CSRF 头，缺 `X-XSRF-TOKEN` 时拒绝。
- [x] **AC-2（FR-4/5）**：密码错/用户不存在/禁用 → 统一 `ENG-AUTH-001`，**不泄露存在性**；成功/失败各产一条登录审计。
- [x] **AC-3（FR-1）**：`auth.mode` 从 [CONFIG-01](CONFIG-01.md) 切 PLATFORM/DELEGATED/BOTH 生效；无 yml 写死，库内非法值按 DELEGATED 失败关闭。
- [x] **AC-4（FR-3）**：`CookieBearerTokenResolver` 从 cookie 解析 JWT，回退 Bearer 兼容 embed/API。
- [x] **AC-5（FR-6）**：13 角色 dev 种子均可登录；生产 profile 无默认密码（首发 init token + 强制改密 / MFA，呼应 [BASE-11](BASE-11.md)）；H2 / PostgreSQL 15 / Oracle 21 迁移烟测通过，达梦 / 人大金仓真实环境见 `DEFER-001`。
- 关联 A1–A9：A6 合规运维（身份 + 审计）。
- T-GATE：前后端门禁全绿（无假登录/无 localStorage token/无写死 auth.mode）；提交后必须重跑 changed 门禁，避免未提交 diff 扫 0。
- B0 验收：纯确定性认证，天然 B0（无模型依赖）。

## 完工证据
- 代码 permalink：`AuthController` / `AuthService` / `JwtIssuer` / `CookieBearerTokenResolver` / `CsrfDoubleSubmitFilter` / `SecurityConfig` / `SystemConfigService` / `platform_credential` V27（5 方言文件）/ 登录审计。
- 测试：`mvn test`（791 tests / 0 failures / 0 errors，含 H2 / PostgreSQL 15 / Oracle 21 迁移至 V44）· `npm run verify`（39 files / 177 tests）· `npm run build`（通过，`vendor-antd` chunk 提示登记 `DEFER-003`）· 登录签发 cookie+claims · 密码错/禁用/不存在统一拒绝且不泄露 · 登录审计 SUCCESS/FAILED · cookie 解析 · 登出清 cookie · CSRF 缺头拒绝 · `auth.mode` 切换与非法值失败关闭 · 委托 IdP `NOT_CONNECTED` 诚实降级。
- 待处理问题：真实院方 IdP/JWKS/国密证书链 `DEFER-005`；达梦 / 人大金仓真实运行环境 `DEFER-001`；前端依赖审计 `DEFER-002` 与非阻断输出噪声 `DEFER-003` 不写成已清零。
- 审计员签字：@<reviewer>（owner ≠ reviewer，高风险双签）。

## 大卡工序（5d，后端）
- PR1：platform_credential 表 + V27 五方言迁移 + 13 角色 dev 种子 + PasswordEncoder → AC-5。
- PR2：AuthController 登录/登出 + JwtIssuer + CookieBearerTokenResolver + CSRF + 登录审计 → AC-1/2/4。
- PR3：双模 auth.mode（CONFIG-01）+ 内网 IdP 委托挂点 + MFA 机制预留 → AC-3。
